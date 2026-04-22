# Doc 4

Local inference pipeline note 4.

- Use router for task classification.
- Use retriever for evidence grounding.
- Use controller scoring: 10*exec_pass + 3*evidence + 1.5*sem_sim + 0.1*logprob.
- Prefer sandbox execution for generated code.
