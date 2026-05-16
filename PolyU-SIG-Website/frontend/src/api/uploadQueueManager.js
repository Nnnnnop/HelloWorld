/**
 * Frontend Upload Queue Manager
 * 
 * Handles concurrent uploads with:
 * - Configurable concurrency limits (default 5)
 * - Automatic retry on failure (up to 3 attempts)
 * - Progress tracking per file and overall
 * - Pause/Resume functionality
 * - Hash calculation for deduplication
 */

import { computed, ref } from 'vue'

const DEFAULT_MAX_CONCURRENT = 5
const DEFAULT_MAX_RETRIES = 3
const DEFAULT_CHUNK_SIZE = 5242880 // 5MB

export class UploadQueueManager {
  constructor(options = {}) {
    this.sessionId = null
    this.maxConcurrent = options.maxConcurrent || DEFAULT_MAX_CONCURRENT
    this.maxRetries = options.maxRetries || DEFAULT_MAX_RETRIES
    this.chunkSize = options.chunkSize || DEFAULT_CHUNK_SIZE

    this.uploadQueue = []
    this.activeUploads = new Map()
    this.completedUploads = new Map()
    this.failedUploads = new Map()
    this.isPaused = false

    this.progressCallbacks = []
    this.statusChangeCallbacks = []
  }

  /**
   * Initialize upload session
   */
  async initializeSession(sessionId, totalFiles, totalBytes) {
    this.sessionId = sessionId
    this.uploadQueue = []
    this.activeUploads.clear()
    this.completedUploads.clear()
    this.failedUploads.clear()
    this.isPaused = false
  }

  /**
   * Add file to upload queue
   */
  queueFile(file, metadata) {
    const taskId = `${this.sessionId}_${metadata.clientPath}_${Date.now()}`

    const task = {
      id: taskId,
      file,
      metadata,
      status: 'queued', // queued, uploading, success, failed, retrying
      progress: 0,
      uploadedBytes: 0,
      retryCount: 0,
      error: null,
      startTime: null,
      endTime: null,
      fileHash: null
    }

    this.uploadQueue.push(task)
    this.notifyStatusChange(task, 'queued')
    this.processQueue()

    return taskId
  }

  /**
   * Process upload queue with concurrency control
   */
  async processQueue() {
    if (this.isPaused) {
      return
    }

    while (this.activeUploads.size < this.maxConcurrent && this.uploadQueue.length > 0) {
      const task = this.uploadQueue.shift()

      if (task.status !== 'queued' && task.status !== 'retrying') {
        continue
      }

      this.activeUploads.set(task.id, task)
      await this.uploadFile(task)
    }
  }

  /**
   * Upload single file with progress tracking
   */
  async uploadFile(task) {
    try {
      task.status = 'uploading'
      task.startTime = Date.now()
      this.notifyStatusChange(task, 'uploading')

      // Calculate file hash for deduplication
      const fileHash = await this.calculateFileHash(task.file)
      task.fileHash = fileHash

      // Upload file with progress callback
      await this.uploadFileToServer(task, (progressEvent) => {
        const percentComplete = Math.round((progressEvent.loaded / progressEvent.total) * 100)
        task.progress = percentComplete
        task.uploadedBytes = progressEvent.loaded

        this.notifyProgress(task)
      })

      task.status = 'success'
      task.progress = 100
      task.uploadedBytes = task.file.size
      task.endTime = Date.now()

      this.completedUploads.set(task.id, task)
      this.notifyStatusChange(task, 'success')

    } catch (error) {
      task.error = error.message
      task.retryCount++

      if (task.retryCount < this.maxRetries) {
        task.status = 'retrying'
        this.notifyStatusChange(task, 'retrying')

        // Exponential backoff: 1s, 2s, 4s
        const delayMs = Math.pow(2, task.retryCount - 1) * 1000
        await this.delay(delayMs)

        // Re-queue for retry
        this.uploadQueue.unshift(task)
      } else {
        task.status = 'failed'
        task.endTime = Date.now()
        this.failedUploads.set(task.id, task)
        this.notifyStatusChange(task, 'failed')
      }

    } finally {
      this.activeUploads.delete(task.id)

      // Continue with next file in queue
      await this.processQueue()
    }
  }

  /**
   * Actually upload file to server
   */
  async uploadFileToServer(task, onProgress) {
    const formData = new FormData()

    formData.append('sessionId', this.sessionId)
    formData.append('clientPath', task.metadata.clientPath)
    formData.append('displayName', task.metadata.displayName)
    formData.append('folderId', task.metadata.folderId)
    formData.append('category', task.metadata.category || '')
    formData.append('description', task.metadata.description || '')
    formData.append('tags', task.metadata.tags || '')
    formData.append('visibility', task.metadata.visibility || 'HIDDEN')
    formData.append('file', task.file, task.metadata.clientPath)

    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()

      xhr.upload.addEventListener('progress', (event) => {
        if (event.lengthComputable && onProgress) {
          onProgress(event)
        }
      })

      xhr.addEventListener('load', () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(xhr.response)
        } else {
          reject(new Error(`Upload failed with status ${xhr.status}: ${xhr.statusText}`))
        }
      })

      xhr.addEventListener('error', () => {
        reject(new Error('Upload failed - network error'))
      })

      xhr.addEventListener('abort', () => {
        reject(new Error('Upload cancelled'))
      })

      xhr.open('POST', `/api/files/upload/session/${this.sessionId}/file`)
      xhr.send(formData)
    })
  }

  /**
   * Calculate SHA-256 hash of file for deduplication
   */
  async calculateFileHash(file) {
    const chunkSize = Math.min(this.chunkSize, 1024 * 1024) // 1MB chunks for hashing
    const chunks = Math.ceil(file.size / chunkSize)

    const hashBuffer = await crypto.subtle.digest('SHA-256', await this.readFileInChunks(file))
    return this.arrayBufferToHex(hashBuffer)
  }

  /**
   * Read file in chunks for hashing
   */
  async readFileInChunks(file) {
    const chunkSize = Math.min(this.chunkSize, 1024 * 1024)
    const reader = new FileReader()
    let offset = 0

    return new Promise((resolve, reject) => {
      const chunks = []

      const readNextChunk = () => {
        if (offset < file.size) {
          const slice = file.slice(offset, offset + chunkSize)
          reader.onload = (event) => {
            chunks.push(new Uint8Array(event.target.result))
            offset += chunkSize
            readNextChunk()
          }
          reader.onerror = reject
          reader.readAsArrayBuffer(slice)
        } else {
          const totalLength = chunks.reduce((sum, chunk) => sum + chunk.length, 0)
          const buffer = new Uint8Array(totalLength)
          let pos = 0
          for (const chunk of chunks) {
            buffer.set(chunk, pos)
            pos += chunk.length
          }
          resolve(buffer)
        }
      }

      readNextChunk()
    })
  }

  /**
   * Convert ArrayBuffer to hex string
   */
  arrayBufferToHex(buffer) {
    const hashArray = Array.from(new Uint8Array(buffer))
    return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('')
  }

  /**
   * Pause all uploads
   */
  pause() {
    this.isPaused = true
  }

  /**
   * Resume all uploads
   */
  async resume() {
    this.isPaused = false
    await this.processQueue()
  }

  /**
   * Cancel all uploads
   */
  cancel() {
    this.uploadQueue = []
    this.activeUploads.clear()
    this.isPaused = true
  }

  /**
   * Get overall progress
   */
  getOverallProgress() {
    const total = this.completedUploads.size + this.failedUploads.size + this.activeUploads.size + this.uploadQueue.length

    if (total === 0) return 0

    let totalBytes = 0
    let uploadedBytes = 0

    // Count completed files as fully uploaded
    this.completedUploads.forEach((task) => {
      totalBytes += task.file.size
      uploadedBytes += task.file.size
    })

    // Count failed files as 0
    this.failedUploads.forEach((task) => {
      totalBytes += task.file.size
    })

    // Count active uploads with progress
    this.activeUploads.forEach((task) => {
      totalBytes += task.file.size
      uploadedBytes += task.uploadedBytes
    })

    // Count queued files as 0
    this.uploadQueue.forEach((task) => {
      totalBytes += task.file.size
    })

    return totalBytes > 0 ? Math.round((uploadedBytes / totalBytes) * 100) : 0
  }

  /**
   * Get queue statistics
   */
  getStats() {
    return {
      totalQueued: this.uploadQueue.length + this.activeUploads.size + this.completedUploads.size + this.failedUploads.size,
      queued: this.uploadQueue.length,
      active: this.activeUploads.size,
      completed: this.completedUploads.size,
      failed: this.failedUploads.size,
      progress: this.getOverallProgress()
    }
  }

  /**
   * Register progress callback
   */
  onProgress(callback) {
    this.progressCallbacks.push(callback)
  }

  /**
   * Register status change callback
   */
  onStatusChange(callback) {
    this.statusChangeCallbacks.push(callback)
  }

  /**
   * Notify progress
   */
  notifyProgress(task) {
    this.progressCallbacks.forEach((callback) => {
      callback({
        taskId: task.id,
        progress: task.progress,
        uploadedBytes: task.uploadedBytes,
        totalBytes: task.file.size,
        stats: this.getStats()
      })
    })
  }

  /**
   * Notify status change
   */
  notifyStatusChange(task, status) {
    this.statusChangeCallbacks.forEach((callback) => {
      callback({
        taskId: task.id,
        status,
        error: task.error,
        retryCount: task.retryCount,
        stats: this.getStats()
      })
    })
  }

  /**
   * Delay helper
   */
  delay(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms))
  }
}

/**
 * Create Vue 3 composable for upload queue
 */
export function useUploadQueue(options = {}) {
  const manager = new UploadQueueManager(options)

  const stats = ref({
    totalQueued: 0,
    queued: 0,
    active: 0,
    completed: 0,
    failed: 0,
    progress: 0
  })

  const taskStatus = ref(new Map())

  manager.onProgress((event) => {
    stats.value = event.stats
  })

  manager.onStatusChange((event) => {
    taskStatus.value.set(event.taskId, {
      status: event.status,
      error: event.error,
      retryCount: event.retryCount
    })
    stats.value = event.stats
  })

  return {
    manager,
    stats: computed(() => stats.value),
    taskStatus: computed(() => new Map(taskStatus.value)),
    initializeSession: (sessionId, totalFiles, totalBytes) =>
      manager.initializeSession(sessionId, totalFiles, totalBytes),
    queueFile: (file, metadata) => manager.queueFile(file, metadata),
    pause: () => manager.pause(),
    resume: () => manager.resume(),
    cancel: () => manager.cancel(),
    getStats: () => manager.getStats()
  }
}
