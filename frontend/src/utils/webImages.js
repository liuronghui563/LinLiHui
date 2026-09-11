const FALLBACK_IMAGES = [
  'https://picsum.photos/id/1015/1600/720',
  'https://picsum.photos/id/1018/1600/720',
  'https://picsum.photos/id/1036/1600/720',
  'https://picsum.photos/id/1011/1600/720',
  'https://picsum.photos/id/1043/1600/720',
  'https://picsum.photos/id/24/1600/720'
]

export async function fetchWebAdImages(count = 4) {
  const size = Math.max(count, 1)
  try {
    const page = Math.floor(Math.random() * 28) + 1
    const res = await fetch(`https://picsum.photos/v2/list?page=${page}&limit=${size}`)
    if (!res.ok) throw new Error('picsum list failed')
    const list = await res.json()
    if (!Array.isArray(list) || !list.length) throw new Error('empty picsum list')
    return list.map((item) => `https://picsum.photos/id/${item.id}/1600/720`)
  } catch {
    return Array.from({ length: size }, (_, i) => FALLBACK_IMAGES[i % FALLBACK_IMAGES.length])
  }
}

export async function attachWebImages(ads = []) {
  if (!ads.length) return []
  const images = await fetchWebAdImages(ads.length)
  return ads.map((ad, index) => ({
    ...ad,
    imageUrl: images[index] || FALLBACK_IMAGES[index % FALLBACK_IMAGES.length]
  }))
}
