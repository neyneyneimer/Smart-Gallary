import React, { useState, useEffect } from "react";
import { Image as ImageIcon, Sparkles, Cloud, HardDrive, Cpu, RefreshCw, BadgeCheck } from "lucide-react";
import { PhotoEntity, PhotoEditVersionEntity } from "./types";
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
            setSelectedPhoto(null); // Deleted
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

  // Run initial photos query
  useEffect(() => {
    fetchPhotos();
  }, []);

  // Fetch versions whenever selected photo changes
  useEffect(() => {
    if (selectedPhoto) {
      fetchVersions(selectedPhoto.id);
    } else {
      setVersions([]);
    }
  }, [selectedPhoto]);

  // 3. Apply filter effect
  const handleApplyFilter = async (filterName: string) => {
    if (!selectedPhoto) return;
    try {
      const res = await fetch(`/api/photos/${selectedPhoto.id}/filter`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ filterName })
      });
      if (res.ok) {
        await fetchPhotos(); // refresh lists
      }
    } catch (err) {
      console.error("Failed to apply filter", err);
    }
  };

  // 4. Revert to original file
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

  // 5. Revert to specific version historical marker
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

  // 6. Delete photo
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

  // 7. Simulated upload (with deduplication blocking checking)
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

  // 8. Trigger server face grouping scanning
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

  // 9. Trigger server backup sync
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
              await fetchPhotos(); // Scan complete, refresh tags
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
              await fetchPhotos(); // Sync complete, refresh indicators
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

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col font-sans">
      {/* Universal Desktop & Tablet Top-Rail Header */}
      <header className="sticky top-0 z-40 bg-white border-b border-gray-150 shadow-xs px-4 py-3">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-brand-purple text-white flex items-center justify-center shadow-md shadow-brand-purple/20">
              <Sparkles size={18} className="animate-pulse" />
            </div>
            <div>
              <h1 className="text-sm font-extrabold tracking-tight text-gray-900 uppercase font-display flex items-center gap-1">
                Aura AI Gallery
                <span className="text-[9px] font-bold bg-brand-purple/10 text-brand-purple px-1.5 py-0.5 rounded-full lowercase font-mono">
                  v2.0
                </span>
              </h1>
              <span className="text-[10px] text-gray-500 font-medium block">
                Non-Destructive Neural Media Organizer
              </span>
            </div>
          </div>

          {/* Interactive system notifications */}
          <div className="hidden sm:flex items-center gap-4 text-xs font-mono text-gray-500">
            {isScanning && (
              <span className="flex items-center gap-1 text-brand-purple animate-pulse">
                <Cpu size={12} />
                Scanning AI faces {Math.round(scanProgress * 100)}%
              </span>
            )}
            {isSyncing && (
              <span className="flex items-center gap-1 text-blue-600 animate-pulse">
                <RefreshCw size={12} className="animate-spin" />
                Syncing backup {syncCurrent}/{syncTotal}
              </span>
            )}
            {!isScanning && !isSyncing && (
              <span className="flex items-center gap-1 text-green-600">
                <BadgeCheck size={13} />
                Protection Engine Active
              </span>
            )}
          </div>
        </div>
      </header>

      {/* Main Responsive Body Frame */}
      <main className="flex-1 max-w-7xl mx-auto w-full px-4 py-6 pb-24 flex flex-col gap-6">
        {/* Navigation Tab Pills */}
        <div className="flex items-center bg-gray-100 p-1.5 rounded-2xl border border-gray-150 self-start">
          {[
            { id: 0, label: "Gallery Feed", icon: <ImageIcon size={14} /> },
            { id: 1, label: "AI Albums", icon: <Sparkles size={14} /> },
            { id: 2, label: "Cloud Backup", icon: <Cloud size={14} /> },
            { id: 3, label: "Optimizer", icon: <HardDrive size={14} /> }
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => {
                setActiveTab(tab.id);
                // Exit album details automatically if going to a non-gallery view
                if (tab.id !== 0) {
                  setSelectedFaceAlbum(null);
                }
              }}
              className={`flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-bold transition cursor-pointer ${
                activeTab === tab.id
                  ? "bg-white text-brand-purple shadow-sm border border-gray-200/50"
                  : "text-gray-600 hover:text-gray-900"
              }`}
            >
              {tab.icon}
              {tab.label}
            </button>
          ))}
        </div>

        {/* Dynamic Tab Renderers */}
        <div className="flex-1 flex flex-col">
          {activeTab === 0 && (
            <GalleryFeed
              photos={photos}
              selectedFaceAlbum={selectedFaceAlbum}
              onExitFaceAlbum={() => setSelectedFaceAlbum(null)}
              onPhotoSelect={(p) => setSelectedPhoto(p)}
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
                setActiveTab(0); // Switch back to gallery list automatically
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

      {/* Floating full-screen details workspace details panel */}
      {selectedPhoto && (
        <PhotoDetails
          photo={selectedPhoto}
          versions={versions}
          onDismiss={() => setSelectedPhoto(null)}
          onApplyFilter={handleApplyFilter}
          onRevertOriginal={handleRevertOriginal}
          onRevertVersion={handleRevertVersion}
          onDelete={handleDeletePhoto}
        />
      )}

      {/* Touch Target Navigation Bar Footer for Mobile screens */}
      <footer className="fixed bottom-0 inset-x-0 bg-white border-t border-gray-150 py-2.5 px-4 flex justify-around items-center z-30 shadow-lg md:hidden">
        {[
          { id: 0, label: "Feed", icon: <ImageIcon size={18} /> },
          { id: 1, label: "AI Albums", icon: <Sparkles size={18} /> },
          { id: 2, label: "Backup", icon: <Cloud size={18} /> },
          { id: 3, label: "Storage", icon: <HardDrive size={18} /> }
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => {
              setActiveTab(tab.id);
              if (tab.id !== 0) {
                setSelectedFaceAlbum(null);
              }
            }}
            className={`flex flex-col items-center justify-center p-1 cursor-pointer transition ${
              activeTab === tab.id ? "text-brand-purple font-boldScale" : "text-gray-400 hover:text-gray-600"
            }`}
          >
            {tab.icon}
            <span className="text-[9px] mt-1 font-semibold">{tab.label}</span>
          </button>
        ))}
      </footer>
    </div>
  );
}
