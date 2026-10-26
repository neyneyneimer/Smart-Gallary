import React, { useMemo } from "react";
import { Sparkles, Cpu, User, Image as ImageIcon, ChevronRight, CheckCircle2, Trees } from "lucide-react";
import { PhotoEntity } from "../types";

interface AIAlbumsProps {
  photos: PhotoEntity[];
  isScanning: boolean;
  scanProgress: number;
  onStartScan: () => void;
  onAlbumClick: (face: string) => void;
}

export function AIAlbums({
  photos,
  isScanning,
  scanProgress,
  onStartScan,
  onAlbumClick
}: AIAlbumsProps) {
  // Check count of unscanned files
  const unscannedCount = useMemo(() => {
    return photos.filter((p) => p.faceTag === null).length;
  }, [photos]);

  const scannedCount = useMemo(() => {
    return photos.filter((p) => p.faceTag !== null).length;
  }, [photos]);

  // Group photos by face tags
  const groups = useMemo(() => {
    const map: { [key: string]: PhotoEntity[] } = {};
    photos.forEach((photo) => {
      if (photo.faceTag) {
        if (!map[photo.faceTag]) map[photo.faceTag] = [];
        map[photo.faceTag].push(photo);
      }
    });
    return map;
  }, [photos]);

  // Known face tags in our app
  const facesList = ["Alice", "Bob", "Clara", "No Face"];

  return (
    <div className="flex-1 flex flex-col gap-6">
      {/* Smart Analysis Hero Section */}
      <div className="relative overflow-hidden rounded-3xl border border-brand-purple/20 bg-gradient-to-br from-brand-purple/10 via-purple-50/20 to-white p-6 md:p-8">
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div className="max-w-md">
            <div className="flex items-center gap-2 text-brand-purple mb-3">
              <Sparkles size={18} className="animate-pulse" />
              <span className="text-xs font-bold uppercase tracking-widest">
                Aura AI Processing Engine
              </span>
            </div>
            <h2 className="text-2xl font-extrabold tracking-tight text-gray-900 md:text-3xl font-display">
              Face Grouping & Semantic Search
            </h2>
            <p className="text-sm text-gray-600 mt-2 leading-relaxed">
              Scan your media to let the **Gemini** neural core automatically categorize faces, detect attire, environments, and ambient light.
            </p>

            <div className="flex gap-4 mt-4 text-xs font-mono text-gray-500">
              <span>Scanned: <strong>{scannedCount}</strong></span>
              <span>Unscanned: <strong>{unscannedCount}</strong></span>
            </div>
          </div>

          <div className="shrink-0 min-w-[200px]">
            {isScanning ? (
              <div className="flex flex-col gap-2 bg-white/80 p-4 rounded-2xl border border-gray-200">
                <div className="flex justify-between items-center text-xs font-semibold text-gray-700">
                  <span className="flex items-center gap-1.5 animate-pulse">
                    <Cpu size={14} className="text-brand-purple" />
                    Analysing media...
                  </span>
                  <span>{Math.round(scanProgress * 100)}%</span>
                </div>
                <div className="w-full h-2 bg-gray-150 rounded-full overflow-hidden mt-1">
                  <div
                    className="h-full bg-brand-purple transition-all duration-300"
                    style={{ width: `${scanProgress * 100}%` }}
                  />
                </div>
              </div>
            ) : unscannedCount > 0 ? (
              <button
                onClick={onStartScan}
                className="w-full flex items-center justify-center gap-2 bg-brand-purple hover:bg-brand-purple/95 text-white font-semibold px-6 py-3 rounded-2xl shadow-lg shadow-brand-purple/25 hover:shadow-brand-purple/35 transition hover:scale-[1.02] active:scale-95 cursor-pointer"
              >
                <Cpu size={18} />
                Scan {unscannedCount} Media Files
              </button>
            ) : (
              <div className="flex items-center gap-2.5 bg-green-50 text-green-700 border border-green-200 px-5 py-3 rounded-2xl">
                <CheckCircle2 size={20} className="shrink-0" />
                <span className="text-xs font-bold">All media categorized!</span>
              </div>
            )}
          </div>
        </div>

        {/* Backdrop Ambient Circle */}
        <div className="absolute -right-16 -bottom-16 w-48 h-48 rounded-full bg-brand-purple/5 filter blur-2xl" />
      </div>

      {/* Grid of Albums */}
      <div className="flex flex-col gap-3">
        <h3 className="text-sm font-semibold text-gray-500 uppercase tracking-wider pl-1">
          Smart Categories
        </h3>

        {scannedCount === 0 ? (
          <div className="flex flex-col items-center justify-center p-12 bg-white border border-gray-150 rounded-2xl text-center">
            <Cpu size={36} className="text-gray-300 mb-2" />
            <span className="text-sm font-bold text-gray-700">No categorised albums yet</span>
            <p className="text-xs text-gray-500 max-w-xs mt-1">
              Click the scan button above to analyze your photos and build semantic face structures.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {facesList.map((face) => {
              const albumPhotos = groups[face] || [];
              if (albumPhotos.length === 0) return null;

              const isScenic = face === "No Face";
              const displayName = isScenic ? "Scenic & Landscapes" : face;
              const displayIcon = isScenic ? <Trees size={16} /> : <User size={16} />;

              const videoCount = albumPhotos.filter((p) => p.durationSeconds > 0).length;
              const photoCount = albumPhotos.length - videoCount;

              const desc = buildCountString(photoCount, videoCount);

              return (
                <div
                  key={face}
                  onClick={() => onAlbumClick(face)}
                  className="flex items-center justify-between p-4 bg-white border border-gray-150 rounded-2xl cursor-pointer hover:border-brand-purple/40 hover:shadow-sm transition"
                >
                  <div className="flex items-center gap-4">
                    {/* Thumbnail */}
                    <div className="w-16 h-16 rounded-xl overflow-hidden border border-gray-100 shrink-0 bg-gray-50">
                      <img
                        src={`/images/${albumPhotos[0].resourceName}.jpg`}
                        alt={displayName}
                        referrerPolicy="no-referrer"
                        className="w-full h-full object-cover"
                      />
                    </div>

                    <div>
                      <h4 className="text-sm font-bold text-gray-900">{displayName}</h4>
                      <div className="flex items-center gap-1 text-xs text-gray-500 mt-1 font-medium">
                        {displayIcon}
                        <span>{desc}</span>
                      </div>
                    </div>
                  </div>

                  <ChevronRight size={18} className="text-gray-400" />
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

function buildCountString(photosCount: number, videosCount: number) {
  const parts: string[] = [];
  if (photosCount > 0) {
    parts.push(`${photosCount} ${photosCount === 1 ? "photo" : "photos"}`);
  }
  if (videosCount > 0) {
    parts.push(`${videosCount} ${videosCount === 1 ? "video" : "videos"}`);
  }
  return parts.join(", ");
}
