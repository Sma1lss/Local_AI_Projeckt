from __future__ import annotations

import argparse
from pathlib import Path

from services import retriever


def main() -> int:
    parser = argparse.ArgumentParser(description="Build retriever index")
    parser.add_argument("--docs-dir", type=Path, default=Path("data/docs"))
    parser.add_argument("--index-dir", type=Path, default=Path("data/index"))
    args = parser.parse_args()

    retriever.DOCS_DIR = args.docs_dir.resolve()
    retriever.INDEX_DIR = args.index_dir.resolve()
    retriever.INDEX_DIR.mkdir(parents=True, exist_ok=True)
    retriever.DOCS_JSON = retriever.INDEX_DIR / "docs.json"
    retriever.EMBS_JSON = retriever.INDEX_DIR / "embeddings.json"
    retriever.FAISS_INDEX = retriever.INDEX_DIR / "faiss.index"

    docs = retriever.load_documents(retriever.DOCS_DIR)
    if not docs:
        print(f"No docs found in {retriever.DOCS_DIR}")
        return 1

    texts = [d["text"] for d in docs]
    embs = retriever.embed_texts(texts)
    retriever.save_index(docs, embs)

    print(f"Indexed docs: {len(docs)}")
    print(f"Vector dim: {len(embs[0]) if embs else 0}")

    if retriever.faiss is not None and retriever.np is not None:
        arr = retriever.np.array(embs, dtype="float32")
        idx = retriever.faiss.IndexFlatL2(arr.shape[1])
        idx.add(arr)
        retriever.faiss.write_index(idx, str(retriever.FAISS_INDEX))
        print(f"FAISS index written: {retriever.FAISS_INDEX}")
    else:
        print("FAISS unavailable, using JSON embedding index fallback")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
