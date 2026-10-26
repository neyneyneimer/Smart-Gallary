export interface ImageAdjustments {
  brightness: number; // -100 to 100, default 0
  contrast: number; // -100 to 100, default 0
  saturation: number; // -100 to 100, default 0
  warmth: number; // -100 to 100, default 0
}

export interface PhotoEntity {
  id: number;
  resourceName: string; // e.g. "img_face_alice", "img_face_bob", "img_face_clara", "img_scenic_mountain", "img_scenic_lake"
  title: string;
  sizeBytes: number;
  dateTaken: number;
  mimeType: string;
  durationSeconds: number; // 0 if image, >0 if video
  faceTag: string | null; // "Alice", "Bob", "Clara", "No Face", or null (for unscanned)
  originalContentHash: string;
  hasBackupSynced: boolean;
  backupLocation: string | null; // "AuraCloud Storage", "Google Photos Storage", "OneDrive Storage", "Dropbox Storage"
  currentFilter: string | null; // Vintage, Mono, Warm Glow, Cool Mist, Cyberpunk, or null
  adjustments?: ImageAdjustments;
  isFavorite?: boolean;
  attire: string | null;
  environment: string | null;
  lighting: string | null;
  cameraModel?: string;
  lens?: string;
  focalLength?: string;
  aperture?: string;
  iso?: number;
  dimensions?: { width: number; height: number };
}

export interface PhotoEditVersionEntity {
  id: number;
  photoId: number;
  versionNumber: number;
  filterName: string;
  adjustments?: ImageAdjustments;
  timestamp: number;
  sizeDifferenceBytes: number;
}
