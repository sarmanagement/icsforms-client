## Editor setup

`.editorconfig` is the source of truth for basic whitespace and line endings.

### Vim/Neovim (optional)
This repo includes optional repo-local Vim config in `.vim/`.

To enable per session:

```bash
vim -u NONE -c "set rtp^=$PWD/.vim rtp+=$PWD/.vim" -c "filetype plugin on" -c "syntax on"
```

Or wire it into your personal Vim config for this repo only.
