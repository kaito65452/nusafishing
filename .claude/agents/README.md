# Peringatan Konfigurasi Agent

> ⚠️ **PERHATIAN**: Konfigurasi `model: worker-combo` pada semua file agent di direktori ini (`.claude/agents/*.md`) merupakan custom model combo dari endpoint **9router**.
> Jika Anda menggunakan **Claude Code** resmi / direct Anthropic API, nilai `model: worker-combo` **wajib diganti** (misalnya `haiku`, `sonnet`, `opus`, atau baris `model:` dihapus agar menggunakan default session), karena model `worker-combo` tidak tersedia di API Anthropic standar.
