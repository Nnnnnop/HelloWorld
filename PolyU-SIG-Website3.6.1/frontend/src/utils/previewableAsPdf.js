/**
 * Keep in sync with {@code DocumentPreviewService} (OFFICE_EXTENSIONS, TEXT_PREVIEW_EXTENSIONS, special basenames).
 */
export const OFFICE_PREVIEW_EXTENSIONS = new Set(['docx', 'xlsx', 'pptx'])

export const TEXT_TO_PDF_EXTENSIONS = new Set([
  'txt',
  'json',
  'csv',
  'tsv',
  'log',
  'md',
  'markdown',
  'htm',
  'html',
  'xhtml',
  'xml',
  'yaml',
  'yml',
  'toml',
  'ini',
  'cfg',
  'conf',
  'properties',
  'editorconfig',
  'c',
  'h',
  'cc',
  'cpp',
  'cxx',
  'hh',
  'hpp',
  'hxx',
  'cs',
  'java',
  'kt',
  'kts',
  'rs',
  'go',
  'py',
  'pyw',
  'rb',
  'php',
  'swift',
  'scala',
  'gradle',
  'groovy',
  'nim',
  'js',
  'mjs',
  'cjs',
  'jsx',
  'ts',
  'tsx',
  'vue',
  'svelte',
  'css',
  'scss',
  'sass',
  'less',
  'sql',
  'sh',
  'bash',
  'zsh',
  'fish',
  'bat',
  'cmd',
  'ps1',
  'dockerignore',
  'graphql',
  'gql',
  'env',
  'plist',
  'proto',
  'thrift',
  'r',
  'jl',
  'dart',
  'lua',
  'ex',
  'exs',
  'erl',
  'hs',
  'clj',
  'cljs',
  'edn',
  'fs',
  'fsx',
  'http',
  'tf',
  'hcl',
  'rst',
  'adoc'
])

const SPECIAL_PREVIEW_BASE_NAMES = new Set([
  'dockerfile',
  'makefile',
  'rakefile',
  'gemfile',
  'jenkinsfile',
  'containerfile'
])

export function basenameOf(pathLike) {
  const s = String(pathLike || '').trim().replace(/\\/g, '/')
  const i = s.lastIndexOf('/')
  return i >= 0 ? s.slice(i + 1) : s
}

/** Same rule as backend {@code DocumentPreviewService.extensionOf} (basename only). */
export function extensionOf(fileName) {
  const base = basenameOf(fileName)
  const index = base.lastIndexOf('.')
  if (index < 0 || index === base.length - 1) return ''
  return base.slice(index + 1).toLowerCase()
}

/** True when the main file `/preview` endpoint serves a converted PDF (see DocumentPreviewService.shouldConvertToPdf). */
export function fileNameConvertibleToPdfPreview(fileName) {
  if (!fileName || !String(fileName).trim()) return false
  const base = basenameOf(fileName).toLowerCase()
  if (SPECIAL_PREVIEW_BASE_NAMES.has(base)) return true
  const ext = extensionOf(fileName)
  if (ext === 'pdf') return true
  if (OFFICE_PREVIEW_EXTENSIONS.has(ext)) return true
  return TEXT_TO_PDF_EXTENSIONS.has(ext)
}
