#include "httplib.h"
#include "json.hpp"

#include <algorithm>
#include <chrono>
#include <cctype>
#include <cmath>
#include <ctime>
#include <cstddef>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <future>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <random>
#include <sstream>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#endif

using json = nlohmann::json;
namespace fs = std::filesystem;

struct Endpoint {
    std::string scheme;
    std::string host;
    int port = 0;
    std::string base_path;
};

struct ModelResult {
    std::string model;
    bool ok = false;
    std::string text;
    std::string error;
    int latency_ms = 0;
};

struct LiteratureSnippet {
    std::string doc_id;
    std::string path;
    std::string snippet;
    double score = 0.0;
};

struct Config {
    fs::path base_dir;
    fs::path literature_dir;
    fs::path history_dir;
    std::unordered_map<std::string, std::string> endpoints;
    std::string judge_model;
    std::string judge_url;
    int model_timeout_ms = 20000;
    int judge_timeout_ms = 25000;
    int model_max_tokens = 512;
    int judge_max_tokens = 512;
    double temperature = 0.2;
    size_t max_history_turns = 10;
    size_t context_turns = 10;
    int literature_top_k = 4;
    double literature_min_score = 0.18;
    size_t literature_chunk_chars = 1200;
    size_t literature_snippet_chars = 700;
    std::string host = "0.0.0.0";
    int port = 9005;
};

static std::mutex g_history_mutex;

static std::string trim(const std::string &s) {
    size_t start = 0;
    while (start < s.size() && std::isspace(static_cast<unsigned char>(s[start]))) {
        start++;
    }
    size_t end = s.size();
    while (end > start && std::isspace(static_cast<unsigned char>(s[end - 1]))) {
        end--;
    }
    return s.substr(start, end - start);
}

static std::string to_lower(std::string s) {
    for (char &c : s) {
        c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    }
    return s;
}

static std::string now_iso_utc() {
    auto now = std::chrono::system_clock::now();
    std::time_t t = std::chrono::system_clock::to_time_t(now);
    std::tm tm{};
#ifdef _WIN32
    gmtime_s(&tm, &t);
#else
    gmtime_r(&t, &tm);
#endif
    std::ostringstream oss;
    oss << std::put_time(&tm, "%Y-%m-%dT%H:%M:%SZ");
    return oss.str();
}

static std::string sanitize_chat_id(const std::string &id) {
    if (id.empty()) {
        return "";
    }
    std::string out;
    for (char c : id) {
        if (std::isalnum(static_cast<unsigned char>(c)) || c == '_' || c == '-') {
            out.push_back(c);
        } else {
            out.push_back('_');
        }
    }
    if (out.empty()) {
        return "";
    }
    return out;
}

static std::string generate_chat_id() {
    auto now = std::chrono::system_clock::now();
    auto ts = std::chrono::duration_cast<std::chrono::seconds>(now.time_since_epoch()).count();
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<int> dist(0, 15);
    std::ostringstream oss;
    oss << "chat_" << ts << "_";
    for (int i = 0; i < 6; ++i) {
        oss << std::hex << dist(gen);
    }
    return oss.str();
}

static uint32_t fnv1a(const std::string &s) {
    uint32_t h = 2166136261u;
    for (unsigned char c : s) {
        h ^= c;
        h *= 16777619u;
    }
    return h;
}

static std::vector<std::string> tokenize(const std::string &text) {
    std::vector<std::string> out;
    std::string cur;
    for (char c : text) {
        if (std::isalnum(static_cast<unsigned char>(c)) || c == '_') {
            cur.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
        } else if (!cur.empty()) {
            out.push_back(cur);
            cur.clear();
        }
    }
    if (!cur.empty()) {
        out.push_back(cur);
    }
    return out;
}

static std::vector<float> embed_text(const std::string &text, size_t dim = 256) {
    std::vector<float> vec(dim, 0.0f);
    for (const auto &tok : tokenize(text)) {
        uint32_t h = fnv1a(tok);
        size_t idx = static_cast<size_t>(h % dim);
        float sign = (h & 1u) ? -1.0f : 1.0f;
        vec[idx] += sign;
    }
    double norm = 0.0;
    for (float v : vec) {
        norm += static_cast<double>(v) * static_cast<double>(v);
    }
    if (norm > 0.0) {
        norm = std::sqrt(norm);
        for (float &v : vec) {
            v = static_cast<float>(v / norm);
        }
    }
    return vec;
}

static double cosine(const std::vector<float> &a, const std::vector<float> &b) {
    if (a.empty() || b.empty() || a.size() != b.size()) {
        return 0.0;
    }
    double dot = 0.0;
    double na = 0.0;
    double nb = 0.0;
    for (size_t i = 0; i < a.size(); ++i) {
        dot += static_cast<double>(a[i]) * static_cast<double>(b[i]);
        na += static_cast<double>(a[i]) * static_cast<double>(a[i]);
        nb += static_cast<double>(b[i]) * static_cast<double>(b[i]);
    }
    if (na <= 0.0 || nb <= 0.0) {
        return 0.0;
    }
    return dot / (std::sqrt(na) * std::sqrt(nb));
}

static std::unordered_map<std::string, std::string> load_env_file(const fs::path &path) {
    std::unordered_map<std::string, std::string> out;
    std::ifstream in(path);
    if (!in.is_open()) {
        return out;
    }
    std::string line;
    while (std::getline(in, line)) {
        line = trim(line);
        if (line.empty() || line[0] == '#') {
            continue;
        }
        auto pos = line.find('=');
        if (pos == std::string::npos) {
            continue;
        }
        std::string key = trim(line.substr(0, pos));
        std::string val = trim(line.substr(pos + 1));
        if (!val.empty() && val.front() == '"' && val.back() == '"') {
            val = val.substr(1, val.size() - 2);
        }
        if (!key.empty()) {
            out[key] = val;
        }
    }
    return out;
}

static void merge_env(std::unordered_map<std::string, std::string> &dst, const std::unordered_map<std::string, std::string> &src) {
    for (const auto &kv : src) {
        dst[kv.first] = kv.second;
    }
}

static std::string env_or(const std::string &key, const std::unordered_map<std::string, std::string> &file_env, const std::string &def) {
    const char *v = std::getenv(key.c_str());
    if (v && *v) {
        return std::string(v);
    }
    auto it = file_env.find(key);
    if (it != file_env.end()) {
        return it->second;
    }
    return def;
}

static int env_int(const std::string &key, const std::unordered_map<std::string, std::string> &file_env, int def) {
    try {
        return std::stoi(env_or(key, file_env, std::to_string(def)));
    } catch (...) {
        return def;
    }
}

static double env_double(const std::string &key, const std::unordered_map<std::string, std::string> &file_env, double def) {
    try {
        return std::stod(env_or(key, file_env, std::to_string(def)));
    } catch (...) {
        return def;
    }
}

static fs::path get_executable_path() {
#ifdef _WIN32
    char buf[MAX_PATH];
    DWORD len = GetModuleFileNameA(nullptr, buf, MAX_PATH);
    if (len > 0) {
        return fs::path(std::string(buf, len));
    }
#endif
    return fs::path();
}

static fs::path guess_base_dir() {
    fs::path cwd = fs::current_path();
    fs::path exe = get_executable_path();
    if (!exe.empty()) {
        fs::path dir = exe.parent_path();
        for (int i = 0; i < 4; ++i) {
            if (dir.filename() == "Router") {
                return dir.parent_path();
            }
            if (dir.has_parent_path()) {
                dir = dir.parent_path();
            }
        }
    }
    if (cwd.filename() == "Router" && cwd.has_parent_path()) {
        return cwd.parent_path();
    }
    return cwd;
}

static Endpoint parse_url(const std::string &url, std::string &err) {
    Endpoint ep;
    err.clear();
    std::string u = trim(url);
    if (u.empty()) {
        err = "empty url";
        return ep;
    }
    auto scheme_pos = u.find("://");
    if (scheme_pos == std::string::npos) {
        err = "missing scheme";
        return ep;
    }
    ep.scheme = u.substr(0, scheme_pos);
    std::string rest = u.substr(scheme_pos + 3);

    std::string host_port;
    std::string path;
    auto slash_pos = rest.find('/');
    if (slash_pos == std::string::npos) {
        host_port = rest;
    } else {
        host_port = rest.substr(0, slash_pos);
        path = rest.substr(slash_pos);
    }

    auto colon_pos = host_port.find(':');
    if (colon_pos == std::string::npos) {
        ep.host = host_port;
        ep.port = (ep.scheme == "https") ? 443 : 80;
    } else {
        ep.host = host_port.substr(0, colon_pos);
        try {
            ep.port = std::stoi(host_port.substr(colon_pos + 1));
        } catch (...) {
            err = "invalid port";
            return ep;
        }
    }

    ep.base_path = path;
    if (!ep.base_path.empty() && ep.base_path.back() == '/') {
        ep.base_path.pop_back();
    }
    return ep;
}

static bool http_post_json(const Endpoint &ep, const std::string &path, const json &payload, int timeout_ms, json &out, std::string &err, int &status) {
    err.clear();
    status = 0;

    if (ep.scheme != "http") {
        err = "only http scheme supported";
        return false;
    }

    httplib::Client cli(ep.host, ep.port);
    cli.set_connection_timeout(std::chrono::milliseconds(timeout_ms));
    cli.set_read_timeout(std::chrono::milliseconds(timeout_ms));

    std::string full_path = ep.base_path + path;
    if (full_path.empty()) {
        full_path = path;
    }

    auto start = std::chrono::steady_clock::now();
    auto res = cli.Post(full_path.c_str(), payload.dump(), "application/json");
    auto end = std::chrono::steady_clock::now();
    (void)start;
    (void)end;

    if (!res) {
        err = "request failed";
        return false;
    }

    status = res->status;
    if (status < 200 || status >= 300) {
        err = "http status " + std::to_string(status);
        return false;
    }

    try {
        out = json::parse(res->body);
    } catch (...) {
        err = "invalid json";
        return false;
    }
    return true;
}

static ModelResult call_model(const std::string &model, const std::string &url, const std::string &prompt, int max_tokens, double temperature, int timeout_ms) {
    ModelResult result;
    result.model = model;

    std::string err;
    Endpoint ep = parse_url(url, err);
    if (!err.empty()) {
        result.error = err;
        return result;
    }

    json payload = {
        {"prompt", prompt},
        {"max_tokens", max_tokens},
        {"temperature", temperature}
    };

    json out;
    int status = 0;
    auto start = std::chrono::steady_clock::now();
    bool ok = http_post_json(ep, "/infer", payload, timeout_ms, out, err, status);
    auto end = std::chrono::steady_clock::now();
    result.latency_ms = static_cast<int>(std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count());

    if (!ok) {
        result.error = err;
        return result;
    }

    if (out.contains("text")) {
        result.text = out["text"].get<std::string>();
        result.ok = true;
        return result;
    }

    result.error = "missing text field";
    return result;
}

class LiteratureIndex {
public:
    void build(const fs::path &dir, size_t chunk_chars) {
        chunks_.clear();
        if (!fs::exists(dir)) {
            return;
        }

        std::vector<std::string> exts = {
            ".txt", ".md", ".rst", ".py", ".java", ".cpp", ".c", ".h", ".json"
        };

        for (auto &p : fs::recursive_directory_iterator(dir)) {
            if (!p.is_regular_file()) {
                continue;
            }
            std::string ext = to_lower(p.path().extension().string());
            if (std::find(exts.begin(), exts.end(), ext) == exts.end()) {
                continue;
            }

            std::ifstream in(p.path(), std::ios::binary);
            if (!in.is_open()) {
                continue;
            }
            std::ostringstream oss;
            oss << in.rdbuf();
            std::string text = oss.str();
            if (text.empty()) {
                continue;
            }

            for (size_t i = 0; i < text.size(); i += chunk_chars) {
                std::string chunk = text.substr(i, chunk_chars);
                chunk = trim(chunk);
                if (chunk.empty()) {
                    continue;
                }
                Chunk c;
                c.doc_id = p.path().filename().string() + "::" + std::to_string(i / chunk_chars);
                c.path = p.path().string();
                c.text = chunk;
                c.embedding = embed_text(chunk);
                chunks_.push_back(std::move(c));
            }
        }
    }

    std::vector<LiteratureSnippet> search(const std::string &query, int top_k, double min_score, size_t snippet_chars) const {
        std::vector<LiteratureSnippet> out;
        if (chunks_.empty()) {
            return out;
        }
        std::vector<float> qvec = embed_text(query);
        std::vector<std::pair<size_t, double>> scored;
        scored.reserve(chunks_.size());
        for (size_t i = 0; i < chunks_.size(); ++i) {
            double score = cosine(qvec, chunks_[i].embedding);
            scored.emplace_back(i, score);
        }
        std::sort(scored.begin(), scored.end(), [](const auto &a, const auto &b) {
            return a.second > b.second;
        });

        for (int i = 0; i < top_k && i < static_cast<int>(scored.size()); ++i) {
            if (scored[i].second < min_score) {
                continue;
            }
            const auto &c = chunks_[scored[i].first];
            LiteratureSnippet sn;
            sn.doc_id = c.doc_id;
            sn.path = c.path;
            sn.score = scored[i].second;
            sn.snippet = c.text.substr(0, snippet_chars);
            out.push_back(std::move(sn));
        }
        return out;
    }

    bool empty() const {
        return chunks_.empty();
    }

private:
    struct Chunk {
        std::string doc_id;
        std::string path;
        std::string text;
        std::vector<float> embedding;
    };
    std::vector<Chunk> chunks_;
};

static std::vector<json> read_jsonl(const fs::path &file) {
    std::vector<json> out;
    std::ifstream in(file);
    if (!in.is_open()) {
        return out;
    }
    std::string line;
    while (std::getline(in, line)) {
        line = trim(line);
        if (line.empty()) {
            continue;
        }
        try {
            out.push_back(json::parse(line));
        } catch (...) {
            continue;
        }
    }
    return out;
}

static void write_jsonl(const fs::path &file, const std::vector<json> &records) {
    fs::create_directories(file.parent_path());
    std::ofstream out(file, std::ios::trunc);
    if (!out.is_open()) {
        return;
    }
    for (const auto &rec : records) {
        out << rec.dump() << "\n";
    }
}

static void append_jsonl(const fs::path &file, const json &record, size_t max_records) {
    std::vector<json> records = read_jsonl(file);
    if (max_records > 0 && records.size() >= max_records) {
        size_t keep = max_records - 1;
        if (keep > 0 && records.size() > keep) {
            records.erase(records.begin(), records.end() - static_cast<std::ptrdiff_t>(keep));
        } else if (keep == 0) {
            records.clear();
        }
    }
    records.push_back(record);
    write_jsonl(file, records);
}

static int64_t next_turn_id(const fs::path &file) {
    auto records = read_jsonl(file);
    if (records.empty()) {
        return 1;
    }
    const auto &last = records.back();
    if (last.contains("turn_id")) {
        try {
            return last["turn_id"].get<int64_t>() + 1;
        } catch (...) {
            return static_cast<int64_t>(records.size() + 1);
        }
    }
    return static_cast<int64_t>(records.size() + 1);
}

static std::string build_history_block(const std::vector<json> &history) {
    if (history.empty()) {
        return "";
    }
    std::ostringstream oss;
    oss << "CHAT HISTORY:\n";
    for (const auto &rec : history) {
        std::string q = rec.value("question", "");
        std::string a = rec.value("answer", "");
        if (!q.empty()) {
            oss << "User: " << q << "\n";
        }
        if (!a.empty()) {
            oss << "Assistant: " << a << "\n";
        }
    }
    return oss.str();
}

static std::string build_evidence_block(const std::vector<LiteratureSnippet> &snippets) {
    if (snippets.empty()) {
        return "";
    }
    std::ostringstream oss;
    oss << "EVIDENCE:\n";
    for (const auto &sn : snippets) {
        oss << "[" << sn.doc_id << "] " << sn.snippet << "\n";
    }
    return oss.str();
}

static std::unordered_map<std::string, std::string> parse_endpoints_json(const std::string &json_str) {
    std::unordered_map<std::string, std::string> out;
    if (json_str.empty()) {
        return out;
    }
    try {
        auto j = json::parse(json_str);
        if (j.is_object()) {
            for (auto it = j.begin(); it != j.end(); ++it) {
                if (it.value().is_string()) {
                    out[it.key()] = it.value().get<std::string>();
                }
            }
        }
    } catch (...) {
        return out;
    }
    return out;
}

static std::unordered_map<std::string, std::string> load_endpoints(const fs::path &base_dir, const std::unordered_map<std::string, std::string> &file_env) {
    std::unordered_map<std::string, std::string> endpoints;
    std::string env_json = env_or("EXPERT_ENDPOINTS", file_env, "");
    endpoints = parse_endpoints_json(env_json);

    if (!endpoints.empty()) {
        return endpoints;
    }

    fs::path local_cfg = base_dir / "Router" / "config" / "endpoints.json";
    if (fs::exists(local_cfg)) {
        try {
            std::ifstream in(local_cfg);
            json j;
            in >> j;
            if (j.is_object()) {
                for (auto it = j.begin(); it != j.end(); ++it) {
                    if (it.value().is_string()) {
                        endpoints[it.key()] = it.value().get<std::string>();
                    }
                }
            }
        } catch (...) {
        }
    }

    if (!endpoints.empty()) {
        return endpoints;
    }

    fs::path env_path = base_dir / "Python_AI_Model" / ".env";
    if (fs::exists(env_path)) {
        auto env_map = load_env_file(env_path);
        endpoints = parse_endpoints_json(env_or("EXPERT_ENDPOINTS", env_map, ""));
    }

    if (!endpoints.empty()) {
        return endpoints;
    }

    fs::path env_example = base_dir / "Python_AI_Model" / ".env.example";
    if (fs::exists(env_example)) {
        auto env_map = load_env_file(env_example);
        endpoints = parse_endpoints_json(env_or("EXPERT_ENDPOINTS", env_map, ""));
    }

    if (!endpoints.empty()) {
        return endpoints;
    }

    fs::path legacy_env = base_dir / ".env";
    if (fs::exists(legacy_env)) {
        auto env_map = load_env_file(legacy_env);
        endpoints = parse_endpoints_json(env_or("EXPERT_ENDPOINTS", env_map, ""));
    }

    if (!endpoints.empty()) {
        return endpoints;
    }

    fs::path legacy_env_example = base_dir / ".env.example";
    if (fs::exists(legacy_env_example)) {
        auto env_map = load_env_file(legacy_env_example);
        endpoints = parse_endpoints_json(env_or("EXPERT_ENDPOINTS", env_map, ""));
    }

    return endpoints;
}

static Config load_config() {
    Config cfg;
    fs::path base_guess = guess_base_dir();

    auto shared_env = load_env_file(base_guess / "Python_AI_Model" / ".env");
    auto legacy_env = load_env_file(base_guess / ".env");
    auto router_env = load_env_file(base_guess / "Router" / ".env");

    auto merged_env = shared_env;
    merge_env(merged_env, legacy_env);
    merge_env(merged_env, router_env);

    auto base_env = legacy_env;
    merge_env(base_env, router_env);

    cfg.base_dir = fs::path(env_or("BASE_DIR", base_env, base_guess.string()));
    cfg.literature_dir = fs::path(env_or("LITERATURE_DIR", merged_env, (cfg.base_dir / "literature").string()));
    cfg.history_dir = fs::path(env_or("ROUTER_HISTORY_DIR", merged_env, (cfg.base_dir / "Router" / "history").string()));
    cfg.endpoints = load_endpoints(cfg.base_dir, merged_env);

    cfg.judge_model = env_or("JUDGE_MODEL", merged_env, "llama3");
    cfg.judge_url = env_or("JUDGE_URL", merged_env, "");

    cfg.model_timeout_ms = env_int("MODEL_TIMEOUT_MS", merged_env, cfg.model_timeout_ms);
    cfg.judge_timeout_ms = env_int("JUDGE_TIMEOUT_MS", merged_env, cfg.judge_timeout_ms);
    cfg.model_max_tokens = env_int("MODEL_MAX_TOKENS", merged_env, cfg.model_max_tokens);
    cfg.judge_max_tokens = env_int("JUDGE_MAX_TOKENS", merged_env, cfg.judge_max_tokens);
    cfg.temperature = env_double("MODEL_TEMPERATURE", merged_env, cfg.temperature);
    cfg.max_history_turns = static_cast<size_t>(env_int("HISTORY_MAX_TURNS", merged_env, static_cast<int>(cfg.max_history_turns)));
    cfg.context_turns = static_cast<size_t>(env_int("HISTORY_CONTEXT_TURNS", merged_env, static_cast<int>(cfg.context_turns)));
    cfg.literature_top_k = env_int("LITERATURE_TOP_K", merged_env, cfg.literature_top_k);
    cfg.literature_min_score = env_double("LITERATURE_MIN_SCORE", merged_env, cfg.literature_min_score);
    cfg.literature_chunk_chars = static_cast<size_t>(env_int("LITERATURE_CHUNK_CHARS", merged_env, static_cast<int>(cfg.literature_chunk_chars)));
    cfg.literature_snippet_chars = static_cast<size_t>(env_int("LITERATURE_SNIPPET_CHARS", merged_env, static_cast<int>(cfg.literature_snippet_chars)));
    cfg.host = env_or("ROUTER_HOST", merged_env, cfg.host);
    cfg.port = env_int("ROUTER_PORT", merged_env, cfg.port);

    if (cfg.judge_url.empty()) {
        auto it = cfg.endpoints.find(cfg.judge_model);
        if (it != cfg.endpoints.end()) {
            cfg.judge_url = it->second;
        }
    }

    return cfg;
}

int main() {
    Config cfg = load_config();
    fs::create_directories(cfg.history_dir);

    LiteratureIndex lit_index;
    lit_index.build(cfg.literature_dir, cfg.literature_chunk_chars);

    httplib::Server server;
    server.new_task_queue = [] { return new httplib::ThreadPool(8); };

    auto add_cors = [](httplib::Response &res) {
        res.set_header("Access-Control-Allow-Origin", "*");
        res.set_header("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        res.set_header("Access-Control-Allow-Headers", "Content-Type");
    };

    server.Options(R"(.*)", [&](const httplib::Request &, httplib::Response &res) {
        add_cors(res);
        res.status = 204;
    });

    server.Get("/health", [&](const httplib::Request &, httplib::Response &res) {
        json out;
        out["status"] = "ok";
        out["base_dir"] = cfg.base_dir.string();
        out["literature_dir"] = cfg.literature_dir.string();
        out["history_dir"] = cfg.history_dir.string();
        out["endpoints"] = cfg.endpoints;
        out["judge_model"] = cfg.judge_model;
        out["judge_url"] = cfg.judge_url;
        out["literature_indexed"] = !lit_index.empty();
        out["max_history_turns"] = cfg.max_history_turns;
        out["context_turns"] = cfg.context_turns;
        res.set_content(out.dump(2), "application/json");
        add_cors(res);
    });

    server.Post("/ask", [&](const httplib::Request &req, httplib::Response &res) {
        json body;
        try {
            body = json::parse(req.body);
        } catch (...) {
            res.status = 400;
            res.set_content("{\"error\":\"invalid json\"}", "application/json");
            add_cors(res);
            return;
        }

        std::string query;
        if (body.contains("query")) {
            query = body["query"].get<std::string>();
        } else if (body.contains("question")) {
            query = body["question"].get<std::string>();
        } else if (body.contains("message")) {
            query = body["message"].get<std::string>();
        }
        query = trim(query);
        if (query.empty()) {
            res.status = 400;
            res.set_content("{\"error\":\"empty query\"}", "application/json");
            add_cors(res);
            return;
        }

        bool new_chat = false;
        if (body.contains("new_chat")) {
            new_chat = body["new_chat"].get<bool>();
        } else if (body.contains("newChat")) {
            new_chat = body["newChat"].get<bool>();
        }

        std::string chat_id;
        if (body.contains("chat_id")) {
            chat_id = body["chat_id"].get<std::string>();
        } else if (body.contains("chatId")) {
            chat_id = body["chatId"].get<std::string>();
        }
        chat_id = sanitize_chat_id(chat_id);
        if (new_chat || chat_id.empty()) {
            chat_id = generate_chat_id();
            new_chat = true;
        }

        if (cfg.endpoints.empty()) {
            res.status = 500;
            res.set_content("{\"error\":\"no model endpoints configured\"}", "application/json");
            add_cors(res);
            return;
        }

        if (cfg.judge_url.empty()) {
            res.status = 500;
            res.set_content("{\"error\":\"judge model url not configured\"}", "application/json");
            add_cors(res);
            return;
        }

        std::vector<std::pair<std::string, std::string>> workers;
        for (const auto &kv : cfg.endpoints) {
            if (kv.first == cfg.judge_model) {
                continue;
            }
            workers.push_back(kv);
        }

        if (workers.empty()) {
            res.status = 500;
            res.set_content("{\"error\":\"no worker models available (judge is the only model)\"}", "application/json");
            add_cors(res);
            return;
        }

        fs::path chat_dir = cfg.history_dir / chat_id;
        fs::path models_dir = chat_dir / "models";
        fs::create_directories(models_dir);

        fs::path judge_file = chat_dir / "judge.jsonl";

        std::vector<json> judge_history;
        if (!new_chat && fs::exists(judge_file)) {
            judge_history = read_jsonl(judge_file);
            if (cfg.context_turns > 0 && judge_history.size() > cfg.context_turns) {
                judge_history.erase(judge_history.begin(), judge_history.end() - static_cast<long>(cfg.context_turns));
            }
        }

        std::string history_block = build_history_block(judge_history);

        std::vector<LiteratureSnippet> evidence;
        if (!lit_index.empty()) {
            evidence = lit_index.search(query, cfg.literature_top_k, cfg.literature_min_score, cfg.literature_snippet_chars);
        }
        std::string evidence_block = build_evidence_block(evidence);

        std::string base_prompt = "SYSTEM: Answer the user question using evidence if provided.\n";
        if (!history_block.empty()) {
            base_prompt += history_block + "\n";
        }
        if (!evidence_block.empty()) {
            base_prompt += evidence_block + "\n";
        }
        base_prompt += "QUESTION:\n" + query + "\n";

        std::vector<std::future<ModelResult>> futures;
        futures.reserve(workers.size());
        for (const auto &kv : workers) {
            futures.emplace_back(std::async(std::launch::async, [&cfg, &base_prompt, &kv]() {
                return call_model(kv.first, kv.second, base_prompt, cfg.model_max_tokens, cfg.temperature, cfg.model_timeout_ms);
            }));
        }

        std::vector<ModelResult> results;
        results.reserve(workers.size());
        for (auto &f : futures) {
            results.push_back(f.get());
        }

        std::ostringstream cand_stream;
        cand_stream << "CANDIDATES:\n";
        for (const auto &r : results) {
            cand_stream << "[" << r.model << "]\n";
            if (r.ok) {
                cand_stream << r.text << "\n\n";
            } else {
                cand_stream << "[error] " << r.error << "\n\n";
            }
        }

        std::string judge_prompt = "SYSTEM: You are the judge and synthesizer. Build the best final answer.\n";
        if (!history_block.empty()) {
            judge_prompt += history_block + "\n";
        }
        if (!evidence_block.empty()) {
            judge_prompt += evidence_block + "\n";
        }
        judge_prompt += "QUESTION:\n" + query + "\n\n" + cand_stream.str();

        ModelResult judge = call_model(cfg.judge_model, cfg.judge_url, judge_prompt, cfg.judge_max_tokens, cfg.temperature, cfg.judge_timeout_ms);

        int64_t turn_id = next_turn_id(judge_file);
        std::string ts = now_iso_utc();

        {
            std::lock_guard<std::mutex> lock(g_history_mutex);
            for (const auto &r : results) {
                json rec = {
                    {"turn_id", turn_id},
                    {"ts", ts},
                    {"question", query},
                    {"answer", r.text},
                    {"model", r.model},
                    {"ok", r.ok},
                    {"error", r.error}
                };
                fs::path model_file = models_dir / (r.model + ".jsonl");
                append_jsonl(model_file, rec, cfg.max_history_turns);
            }

            json judge_rec = {
                {"turn_id", turn_id},
                {"ts", ts},
                {"question", query},
                {"answer", judge.text},
                {"model", judge.model},
                {"ok", judge.ok},
                {"error", judge.error}
            };
            append_jsonl(judge_file, judge_rec, cfg.max_history_turns);

            fs::path meta_file = chat_dir / "meta.json";
            if (!fs::exists(meta_file)) {
                json meta = {
                    {"chat_id", chat_id},
                    {"created_at", ts}
                };
                fs::create_directories(meta_file.parent_path());
                std::ofstream out(meta_file, std::ios::trunc);
                if (out.is_open()) {
                    out << meta.dump(2);
                }
            }
        }

        json response;
        response["chat_id"] = chat_id;
        response["new_chat"] = new_chat;
        response["answer"] = judge.text;
        response["judge"] = {
            {"model", judge.model},
            {"ok", judge.ok},
            {"error", judge.error},
            {"latency_ms", judge.latency_ms}
        };
        response["used_models"] = json::array();
        response["candidates"] = json::array();
        for (const auto &r : results) {
            response["used_models"].push_back(r.model);
            response["candidates"].push_back({
                {"model", r.model},
                {"ok", r.ok},
                {"error", r.error},
                {"latency_ms", r.latency_ms},
                {"text", r.text}
            });
        }
        response["literature"] = json::array();
        for (const auto &sn : evidence) {
            response["literature"].push_back({
                {"doc_id", sn.doc_id},
                {"path", sn.path},
                {"score", sn.score},
                {"snippet", sn.snippet}
            });
        }

        res.set_content(response.dump(2), "application/json");
        add_cors(res);
    });

    std::cout << "Router service listening on " << cfg.host << ":" << cfg.port << std::endl;
    server.listen(cfg.host.c_str(), cfg.port);
    return 0;
}






