import { Fragment } from 'react'

/**
 * 轻量 Markdown 渲染器（不新增依赖）
 *
 * 覆盖语法：段落/换行、加粗、斜体、行内代码、无序/有序列表、标题、表格、分隔线、块级引用。
 * 全部走 React 元素渲染，不使用 dangerouslySetInnerHTML，规避 XSS 风险。
 */

// 行内样式解析：把一行文本拆成带样式的 fragment 数组
function parseInline(text, keyPrefix = '') {
  const nodes = []
  // 拆成 普通文本 | **加粗** | *斜体* | `代码`
  const regex =
    /(\*\*[^*]+\*\*)|(\*[^*\n]+\*)|(`[^`\n]+`)/g
  let lastIndex = 0
  let match
  let idx = 0
  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      nodes.push(
        <Fragment key={`${keyPrefix}t${idx++}`}>
          {text.slice(lastIndex, match.index)}
        </Fragment>
      )
    }
    const token = match[0]
    if (token.startsWith('**')) {
      nodes.push(<strong key={`${keyPrefix}b${idx++}`}>{token.slice(2, -2)}</strong>)
    } else if (token.startsWith('`')) {
      nodes.push(<code key={`${keyPrefix}c${idx++}`}>{token.slice(1, -1)}</code>)
    } else {
      nodes.push(<em key={`${keyPrefix}i${idx++}`}>{token.slice(1, -1)}</em>)
    }
    lastIndex = match.index + token.length
  }
  if (lastIndex < text.length) {
    nodes.push(
      <Fragment key={`${keyPrefix}t${idx++}`}>{text.slice(lastIndex)}</Fragment>
    )
  }
  return nodes
}

// 解析 markdown 表格行，返回单元格文本数组
function parseTableRow(line) {
  return line
    .trim()
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((cell) => cell.trim())
}

// 判断一行是否为表格分隔行（如 | --- | :---: |）
function isTableSeparator(line) {
  return /^\s*\|?[\s:|-]+\|?$/.test(line) && /-/.test(line)
}

/**
 * 将 markdown 文本渲染为 React 节点数组。
 * @param {string} content markdown 源文本
 * @returns {React.ReactNode[]}
 */
export default function renderMarkdown(content) {
  if (!content) return null
  const lines = String(content).split(/\r?\n/)
  const blocks = []
  let i = 0
  let key = 0

  while (i < lines.length) {
    const line = lines[i]
    const trimmed = line.trim()

    // 空行 -> 段落分隔
    if (!trimmed) {
      i++
      continue
    }

    // 分隔线
    if (/^([-*_])\1{2,}$/.test(trimmed)) {
      blocks.push(<hr key={key++} />)
      i++
      continue
    }

    // 标题
    const heading = trimmed.match(/^(#{1,6})\s+(.*)$/)
    if (heading) {
      const level = heading[1].length
      const H = `h${Math.min(level + 2, 6)}` // 保持层级适中，避免 h1/h2 过大
      blocks.push(
        <H key={key++} className="afc-md-heading">
          {parseInline(heading[2], `h${key}`)}
        </H>
      )
      i++
      continue
    }

    // 引用块
    if (/^>\s?/.test(trimmed)) {
      const quoteLines = []
      while (i < lines.length && /^\s*>\s?/.test(lines[i])) {
        quoteLines.push(lines[i].replace(/^\s*>\s?/, ''))
        i++
      }
      blocks.push(
        <blockquote key={key++}>
          {quoteLines.map((q, qi) => (
            <p key={qi}>{parseInline(q, `q${key}_${qi}`)}</p>
          ))}
        </blockquote>
      )
      continue
    }

    // 无序列表
    const ulMatch = trimmed.match(/^[-*+]\s+(.*)$/)
    if (ulMatch) {
      const items = []
      while (i < lines.length) {
        const t = lines[i].trim()
        const m = t.match(/^[-*+]\s+(.*)$/)
        if (!m) break
        items.push(<li key={items.length}>{parseInline(m[1], `ul${key}_${items.length}`)}</li>)
        i++
      }
      blocks.push(<ul key={key++}>{items}</ul>)
      continue
    }

    // 有序列表
    const olMatch = trimmed.match(/^\d+[.、]\s+(.*)$/)
    if (olMatch) {
      const items = []
      while (i < lines.length) {
        const t = lines[i].trim()
        const m = t.match(/^\d+[.、]\s+(.*)$/)
        if (!m) break
        items.push(<li key={items.length}>{parseInline(m[1], `ol${key}_${items.length}`)}</li>)
        i++
      }
      blocks.push(<ol key={key++}>{items}</ol>)
      continue
    }

    // 表格：检测 表头 + 分隔行 + 至少一行数据
    if (
      /^\|.*\|$/.test(trimmed) &&
      i + 1 < lines.length &&
      isTableSeparator(lines[i + 1]) &&
      i + 2 < lines.length &&
      /^\|.*\|$/.test(lines[i + 2].trim())
    ) {
      const headRow = parseTableRow(trimmed)
      const dataRows = []
      let r = i + 2
      while (r < lines.length) {
        const rt = lines[r].trim()
        if (!/^\|.*\|$/.test(rt)) break
        dataRows.push(parseTableRow(rt))
        r++
      }
      blocks.push(
        <div key={key++} className="afc-md-table-wrap">
          <table className="afc-md-table">
            <thead>
              <tr>
                {headRow.map((cell, ci) => (
                  <th key={ci}>{parseInline(cell, `th${key}_${ci}`)}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {dataRows.map((row, ri) => (
                <tr key={ri}>
                  {row.map((cell, ci) => (
                    <td key={ci}>{parseInline(cell, `td${ri}_${ci}`)}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )
      i = r
      continue
    }

    // 普通段落：合并连续非空文本行，行间用 <br/> 分隔
    const paraLines = []
    while (i < lines.length && lines[i].trim()) {
      paraLines.push(lines[i].trim())
      i++
    }
    blocks.push(
      <p key={key++}>
        {paraLines.map((pl, pli) => (
          <Fragment key={pli}>
            {pli > 0 && <br />}
            {parseInline(pl, `p${key}_${pli}`)}
          </Fragment>
        ))}
      </p>
    )
  }

  if (blocks.length === 0) return null
  return blocks
}