import http from './http'

export function uploadFile(file, purpose, onProgress) {
  const form = new FormData()
  form.append('file', file)
  form.append('purpose', purpose)
  return http.post('/file/upload', form, {
    timeout: 30000,
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    }
  })
}

export function deleteFile(objectKey) {
  return http.delete(`/file/objects/${objectKey}`)
}
