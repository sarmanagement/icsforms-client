if exists('g:loaded_repo_style')
  finish
endif
let g:loaded_repo_style = 1

augroup repo_trim_ws
  autocmd!
  autocmd BufWritePre * if &filetype !=# 'markdown' | %s/\s\+$//e | endif
augroup END
