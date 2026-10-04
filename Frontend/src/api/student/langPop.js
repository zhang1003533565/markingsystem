/**
 * LangPop 语言热度排行（https://langpop.com）
 * 官方每周更新；本项目按自然月缓存一次，避免频繁请求。
 * License: Free to use with attribution to langpop.com
 */

const CACHE_KEY = 'ms_langpop_rankings_monthly_v1';
const API_URL = 'https://langpop.com/api/v1/rankings';
const DEFAULT_LIMIT = 16;

function currentMonthKey() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  return `${now.getFullYear()}-${month}`;
}

function readCache() {
  try {
    const raw = localStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (
      parsed?.month === currentMonthKey() &&
      Array.isArray(parsed?.data) &&
      parsed.data.length > 0
    ) {
      return parsed;
    }
  } catch {
    // ignore broken cache
  }
  return null;
}

function writeCache(payload) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(payload));
  } catch {
    // quota / private mode — ignore
  }
}

/**
 * @param {{ limit?: number, force?: boolean }} [options]
 * @returns {Promise<{
 *   month: string,
 *   weekStart?: string,
 *   generatedAt?: string,
 *   fetchedAt: string,
 *   sourceUrl: string,
 *   data: Array<Record<string, unknown>>
 * }>}
 */
export async function getLangPopRankings(options = {}) {
  const limit = Math.min(Math.max(options.limit || DEFAULT_LIMIT, 1), 50);
  if (!options.force) {
    const cached = readCache();
    if (cached) return cached;
  }

  const response = await fetch(`${API_URL}?limit=${limit}`, {
    method: 'GET',
    headers: { Accept: 'application/json' },
  });

  if (!response.ok) {
    throw new Error(`LangPop 请求失败 (${response.status})`);
  }

  const json = await response.json();
  if (!Array.isArray(json?.data)) {
    throw new Error('LangPop 返回数据无效');
  }

  const payload = {
    month: currentMonthKey(),
    weekStart: json.meta?.week_start || '',
    generatedAt: json.meta?.generated_at || '',
    fetchedAt: new Date().toISOString(),
    sourceUrl: json.meta?.url || 'https://langpop.com',
    data: json.data,
  };

  writeCache(payload);
  return payload;
}
