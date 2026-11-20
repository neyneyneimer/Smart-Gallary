import React, { useState, useEffect } from "react";
import {
  Image as ImageIcon,
  Sparkles,
  Cloud,
  HardDrive,
  Cpu,
  RefreshCw,
  BadgeCheck,
  Camera
} from "lucide-react";
import { PhotoEntity, PhotoEditVersionEntity, ImageAdjustments } from "./types";
import { GalleryFeed } from "./components/GalleryFeed";
import { AIAlbums } from "./components/AIAlbums";
import { CloudSync } from "./components/CloudSync";
import { StorageOptimizer } from "./components/StorageOptimizer";
import { PhotoDetails } from "./components/PhotoDetails";

export default function App() {
  const [photos, setPhotos] = useState<PhotoEntity[]>([]);
  const [selectedPhoto, setSelectedPhoto] = useState<PhotoEntity | null>(null);
  const [selectedFaceAlbum, setSelectedFaceAlbum] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<number>(0);
  const [activeBackupCloud, setActiveBackupCloud] = useState<string>("AuraCloud Storage");

  // Selected Photo Versions state
  const [versions, setVersions] = useState<PhotoEditVersionEntity[]>([]);

  // Background Scanning States
  const [isScanning, setIsScanning] = useState(false);
  const [scanProgress, setScanProgress] = useState(0);

  // Background Syncing States
  const [isSyncing, setIsSyncing] = useState(false);
  const [syncProgress, setSyncProgress] = useState(0);
  const [syncCurrent, setSyncCurrent] = useState(0);
  const [syncTotal, setSyncTotal] = useState(0);

  // Simulation Import Alert State
  const [importResult, setImportResult] = useState<{
    status: "duplicate" | "success" | null;
    photo?: PhotoEntity;
    duplicateOf?: PhotoEntity;
  } | null>(null);

  // 1. Fetch all photos from server
  const fetchPhotos = async () => {
    try {
      const res = await fetch("/api/photos");
      if (res.ok) {
        const data = await res.json();
        setPhotos(data);

        // Keep selected photo synced with latest server edits
        if (selectedPhoto) {
          const updatedSelected = data.find((p: PhotoEntity) => p.id === selectedPhoto.id);
          if (updatedSelected) {
            setSelectedPhoto(updatedSelected);
          } else {
            setSelectedPhoto(null);
          }
        }
      }
    } catch (err) {
      console.error("Failed to load photos", err);
    }
  };

  // 2. Fetch versions for selected photo
  const fetchVersions = async (photoId: number) => {
    try {
      const res = await fetch(`/api/photos/${photoId}/versions`);
      if (res.ok) {
        const data = await res.json();
        setVersions(data);
      }
    } catch (err) {
      console.error("Failed to load photo versions", err);
    }
  };

  useEffect(() => {
    fetchPhotos();
  }, []);

  useEffect(() => {
    if (selectedPhoto) {
      fetchVersions(selectedPhoto.id);
    } else {
      setVersions([]);
    }
  }, [selectedPhoto]);

  // 3. Apply preset filter
  const handleApplyFilter = async (filterName: string) => {
    if (!selectedPhoto) return;
    try {
      const res = await fetch(`/api/photos/${selectedPhoto.id}/filter`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ filterName })
      });
      if (res.ok) {
        await fetchPhotos();
      }
    } catch (err) {
      console.error("Failed to apply filter", err);
    }
  };

  // 4. Apply fine-tuning adjustments
  const handleApplyAdjustments = async (adjustments: ImageAdjustments) => {
    if (!selectedPhoto) return;
    try {
      const res = await fetch(`/api/photos/${selectedPhoto.id}/adjustments`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ adjustments })
      });
      if (res.ok) {
        await fetchPhotos();
      }
    } catch (err) {
      console.error("Failed to apply adjustments", err);
    }
  };

  // 5. Toggle favorite status
  const handleToggleFavorite = async (photoId: number) => {
    try {
      const res = await fetch(`/api/photos/${photoId}/favorite`, {
        method: "PATCH"
      });
      if (res.ok) {
        await fetchPhotos();
      }
    } catch (err) {
      console.error("Failed to toggle favorite", err);
    }
  };

  // 6. Revert to original
  const handleRevertOriginal = async () => {
    if (!selectedPhoto) return;
    try {
      const res = await fetch(`/api/photos/${selectedPhoto.id}/revert-original`, {
        method: "POST"
      });
      if (res.ok) {
        await fetchPhotos();
      }
    } catch (err) {
      console.error("Failed to revert original", err);
    }
  };

  // 7. Revert to specific version
  const handleRevertVersion = async (v: PhotoEditVersionEntity) => {
    if (!selectedPhoto) return;
    try {
      const res = await fetch(`/api/photos/${selectedPhoto.id}/revert-version`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ versionNumber: v.versionNumber })
      });
      if (res.ok) {
        await fetchPhotos();
      }
    } catch (err) {
      console.error("Failed to revert version", err);
    }
  };

  // 8. Delete photo
  const handleDeletePhoto = async () => {
    if (!selectedPhoto) return;
    try {
      const res = await fetch(`/api/photos/${selectedPhoto.id}`, {
        method: "DELETE"
      });
      if (res.ok) {
        setSelectedPhoto(null);
        await fetchPhotos();
      }
    } catch (err) {
      console.error("Failed to delete photo", err);
    }
  };

  // 9. Batch sync photos
  const handleBatchSync = async (photoIds: number[]) => {
    try {
      const res = await fetch("/api/photos/batch-sync", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ photoIds, targetCloud: activeBackupCloud })
      });
      if (res.ok) {
        await fetchPhotos();
      }
    } catch (err) {
      console.error("Batch sync failed", err);
    }
  };

  // 10. Batch delete photos
  const handleBatchDelete = async (photoIds: number[]) => {
    try {
      const res = await fetch("/api/photos/batch-delete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ photoIds })
      });
      if (res.ok) {
        await fetchPhotos();
      }
    } catch (err) {
      console.error("Batch delete failed", err);
    }
  };

  // 11. Simulated upload
  const handleSimulateImport = async (params: {
    resourceName: string;
    title: string;
    sizeBytes: number;
    mimeType?: string;
    durationSeconds?: number;
  }) => {
    try {
      const res = await fetch("/api/photos/import", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(params)
      });
      if (res.ok) {
        const data = await res.json();
        setImportResult(data);
        await fetchPhotos();
      }
    } catch (err) {
      console.error("Import simulation failed", err);
    }
  };

  // 12. Trigger AI Scanning
  const handleStartScan = async () => {
    try {
      const res = await fetch("/api/photos/scan", { method: "POST" });
      if (res.ok) {
        setIsScanning(true);
        setScanProgress(0);
      }
    } catch (err) {
      console.error("Failed to trigger face grouping", err);
    }
  };

  // 13. Trigger Cloud Sync
  const handleStartSync = async () => {
    try {
      const res = await fetch("/api/photos/sync", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ cloudAppName: activeBackupCloud })
      });
      if (res.ok) {
        setIsSyncing(true);
        setSyncProgress(0);
      }
    } catch (err) {
      console.error("Failed to trigger cloud backup sync", err);
    }
  };

  // Polling server background processes state
  useEffect(() => {
    let scanTimer: NodeJS.Timeout | null = null;
    let syncTimer: NodeJS.Timeout | null = null;

    if (isScanning) {
      scanTimer = setInterval(async () => {
        try {
          const res = await fetch("/api/photos/scan-status");
          if (res.ok) {
            const data = await res.json();
            setIsScanning(data.isScanning);
            setScanProgress(data.scanProgress);
            if (!data.isScanning) {
              await fetchPhotos();
            }
          }
        } catch (err) {
          console.error("Polling scan status failed", err);
        }
      }, 500);
    }

    if (isSyncing) {
      syncTimer = setInterval(async () => {
        try {
          const res = await fetch("/api/photos/sync-status");
          if (res.ok) {
            const data = await res.json();
            setIsSyncing(data.isSyncing);
            setSyncProgress(data.syncProgress);
            setSyncCurrent(data.syncCurrent);
            setSyncTotal(data.syncTotal);
            if (!data.isSyncing) {
              await fetchPhotos();
            }
          }
        } catch (err) {
          console.error("Polling sync status failed", err);
        }
      }, 500);
    }

    return () => {
      if (scanTimer) clearInterval(scanTimer);
      if (syncTimer) clearInterval(syncTimer);
    };
  }, [isScanning, isSyncing]);

  // Compute previous and next photo in current view
  const currentPhotoIndex = selectedPhoto
    ? photos.findIndex((p) => p.id === selectedPhoto.id)
    : -1;
  const hasPrev = currentPhotoIndex > 0;
  const hasNext = currentPhotoIndex !== -1 && currentPhotoIndex < photos.length - 1;

  const handleNavigatePrev = () => {
    if (hasPrev) setSelectedPhoto(photos[currentPhotoIndex - 1]);
  };

  const handleNavigateNext = () => {
    if (hasNext) setSelectedPhoto(photos[currentPhotoIndex + 1]);
  };

  return (
    <div className="min-h-screen bg-stone-50 flex flex-col font-sans text-stone-900 selection:bg-stone-900 selection:text-white">
      {/* Top Header Bar */}
      <header className="sticky top-0 z-40 bg-white/95 backdrop-blur-md border-b border-stone-200 shadow-2xs px-4 sm:px-6 py-3">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-stone-900 text-white flex items-center justify-center font-bold text-xs tracking-tighter">
              AU
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-sm font-bold tracking-tight text-stone-900 uppercase font-mono">
                  Aura Studio Gallery
                </h1>
                <span className="text-[10px] font-mono text-stone-500">v2.5</span>
              </div>
              <span className="text-[11px] text-stone-500 font-medium block">
                Parametric Neural Media Organizer
              </span>
            </div>
          </div>

          {/* Interactive Status Indicators */}
          <div className="hidden sm:flex items-center gap-4 text-xs font-mono text-stone-500">
            {isScanning && (
              <span className="flex items-center gap-1.5 text-stone-900 animate-pulse font-medium">
                <Cpu size={13} />
                Scanning embeddings {Math.round(scanProgress * 100)}%
              </span>
            )}
            {isSyncing && (
              <span className="flex items-center gap-1.5 text-stone-900 animate-pulse font-medium">
                <RefreshCw size={13} className="animate-spin" />
                Vault sync {syncCurrent}/{syncTotal}
              </span>
            )}
            {!isScanning && !isSyncing && (
              <span className="flex items-center gap-1.5 text-emerald-700">
                <BadgeCheck size={14} className="text-emerald-600" />
                <span>Vault Protected · 0 Duplicates</span>
              </span>
            )}
          </div>
        </div>
      </header>

      {/* Main Responsive Body */}
      <main className="flex-1 max-w-7xl mx-auto w-full px-4 sm:px-6 py-6 pb-24 flex flex-col gap-6">
        {/* Navigation Tabs (Zero-pill segmented style) */}
        <div className="flex items-center bg-stone-100 p-1 rounded-xl border border-stone-200 self-start">
          {[
            { id: 0, label: "Gallery Feed", icon: <ImageIcon size={14} /> },
            { id: 1, label: "AI Albums", icon: <Sparkles size={14} /> },
            { id: 2, label: "Cloud Vault", icon: <Cloud size={14} /> },
            { id: 3, label: "Optimizer", icon: <HardDrive size={14} /> }
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => {
                setActiveTab(tab.id);
                if (tab.id !== 0) setSelectedFaceAlbum(null);
              }}
              className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-medium transition cursor-pointer ${
                activeTab === tab.id
                  ? "bg-white text-stone-900 shadow-xs font-semibold"
                  : "text-stone-600 hover:text-stone-900"
              }`}
            >
              {tab.icon}
              <span>{tab.label}</span>
            </button>
          ))}
        </div>

        {/* Dynamic Views */}
        <div className="flex-1 flex flex-col">
          {activeTab === 0 && (
            <GalleryFeed
              photos={photos}
              selectedFaceAlbum={selectedFaceAlbum}
              onExitFaceAlbum={() => setSelectedFaceAlbum(null)}
              onPhotoSelect={(p) => setSelectedPhoto(p)}
              onToggleFavorite={handleToggleFavorite}
              onBatchSync={handleBatchSync}
              onBatchDelete={handleBatchDelete}
            />
          )}

          {activeTab === 1 && (
            <AIAlbums
              photos={photos}
              isScanning={isScanning}
              scanProgress={scanProgress}
              onStartScan={handleStartScan}
              onAlbumClick={(face) => {
                setSelectedFaceAlbum(face);
                setActiveTab(0);
              }}
            />
          )}

          {activeTab === 2 && (
            <CloudSync
              photos={photos}
              isSyncing={isSyncing}
              syncProgress={syncProgress}
              syncCurrent={syncCurrent}
              syncTotal={syncTotal}
              syncActiveCloud={activeBackupCloud}
              onChangeCloud={(cloud) => setActiveBackupCloud(cloud)}
              onStartSync={handleStartSync}
            />
          )}

          {activeTab === 3 && (
            <StorageOptimizer
              photos={photos}
              onSimulateImport={handleSimulateImport}
              importResult={importResult}
              onClearImportResult={() => setImportResult(null)}
            />
          )}
        </div>
      </main>

      {/* Full-Screen Photo Details Workbench */}
      {selectedPhoto && (
        <PhotoDetails
          photo={selectedPhoto}
          versions={versions}
          onDismiss={() => setSelectedPhoto(null)}
          onApplyFilter={handleApplyFilter}
          onApplyAdjustments={handleApplyAdjustments}
          onRevertOriginal={handleRevertOriginal}
          onRevertVersion={handleRevertVersion}
          onDelete={handleDeletePhoto}
          onToggleFavorite={() => handleToggleFavorite(selectedPhoto.id)}
          onNavigatePrev={handleNavigatePrev}
          onNavigateNext={handleNavigateNext}
          hasPrev={hasPrev}
          hasNext={hasNext}
        />
      )}

      {/* Mobile Touch Target Navigation Footer */}
      <footer className="fixed bottom-0 inset-x-0 bg-white/95 backdrop-blur-md border-t border-stone-200 py-2 px-4 flex justify-around items-center z-30 shadow-lg md:hidden">
        {[
          { id: 0, label: "Feed", icon: <ImageIcon size={18} /> },
          { id: 1, label: "AI Albums", icon: <Sparkles size={18} /> },
          { id: 2, label: "Vault", icon: <Cloud size={18} /> },
          { id: 3, label: "Optimizer", icon: <HardDrive size={18} /> }
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => {
              setActiveTab(tab.id);
              if (tab.id !== 0) setSelectedFaceAlbum(null);
            }}
            className={`flex flex-col items-center justify-center p-1 cursor-pointer transition ${
              activeTab === tab.id ? "text-stone-900 font-semibold" : "text-stone-400 hover:text-stone-600"
            }`}
          >
            {tab.icon}
            <span className="text-[10px] mt-1 font-medium">{tab.label}</span>
          </button>
        ))}
      </footer>
    </div>
  );
}
