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
  attire: string | null;
  environment: string | null;
  lighting: string | null;
}

export interface PhotoEditVersionEntity {
  id: number;
  photoId: number;
  versionNumber: number;
  filterName: string;
  timestamp: number;
  sizeDifferenceBytes: number;
}
