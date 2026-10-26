import React, { useMemo } from "react";
import {
  Cpu,
  User,
  ChevronRight,
  CheckCircle2,
  Trees,
  Compass,
  Sparkles,
  Camera
} from "lucide-react";
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
  const unscannedCount = useMemo(() => {
    return photos.filter((p) => p.faceTag === null).length;
  }, [photos]);

  const scannedCount = useMemo(() => {
    return photos.filter((p) => p.faceTag !== null).length;
  }, [photos]);

  // Group photos by face tags
  const faceGroups = useMemo(() => {
    const map: { [key: string]: PhotoEntity[] } = {};
    photos.forEach((photo) => {
      if (photo.faceTag) {
        if (!map[photo.faceTag]) map[photo.faceTag] = [];
        map[photo.faceTag].push(photo);
      }
    });
    return map;
  }, [photos]);

  // Group photos by environment/scene
  const sceneGroups = useMemo(() => {
    const map: { [key: string]: PhotoEntity[] } = {};
    photos.forEach((photo) => {
      if (photo.environment) {
        if (!map[photo.environment]) map[photo.environment] = [];
        map[photo.environment].push(photo);
      }
    });
    return map;
  }, [photos]);

  const facesList = ["Alice", "Bob", "Clara", "No Face"];

  return (
    <div className="flex-1 flex flex-col gap-8">
      {/* AI Processing Panel */}
      <div className="bg-white rounded-2xl border border-stone-200 p-6 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-6">
        <div className="max-w-lg">
          <div className="flex items-center gap-2 text-stone-500 text-xs font-mono mb-2">
            <Cpu size={14} className="text-stone-700" />
            <span>Neural Face Grouping & Scene Detection</span>
          </div>
          <h2 className="text-xl font-bold text-stone-900 tracking-tight">
            Smart Recognition Engine
          </h2>
          <p className="text-xs text-stone-500 mt-1 leading-relaxed">
            Neural network clusters photos by facial embeddings and environment signatures without duplicate files.
          </p>

          <div className="flex items-center gap-3 text-xs font-mono text-stone-600 mt-3">
            <span>Indexed: <strong>{scannedCount}</strong></span>
            <span>·</span>
            <span>Pending: <strong>{unscannedCount}</strong></span>
          </div>
        </div>

        <div className="shrink-0 min-w-[220px]">
          {isScanning ? (
            <div className="flex flex-col gap-2 bg-stone-50 p-4 rounded-xl border border-stone-200">
              <div className="flex justify-between items-center text-xs font-mono text-stone-700">
                <span className="flex items-center gap-1.5 animate-pulse">
                  <Cpu size={13} className="text-stone-900" />
                  Analyzing Media...
                </span>
                <span>{Math.round(scanProgress * 100)}%</span>
              </div>
              <div className="w-full h-1.5 bg-stone-200 rounded-full overflow-hidden">
                <div
                  className="h-full bg-stone-900 transition-all duration-300"
                  style={{ width: `${scanProgress * 100}%` }}
                />
              </div>
            </div>
          ) : unscannedCount > 0 ? (
            <button
              onClick={onStartScan}
              className="w-full flex items-center justify-center gap-2 bg-stone-900 hover:bg-stone-800 text-white text-xs font-semibold px-5 py-3 rounded-xl transition cursor-pointer shadow-xs"
            >
              <Cpu size={15} />
              <span>Scan {unscannedCount} Pending Files</span>
            </button>
          ) : (
            <div className="flex items-center gap-2.5 bg-emerald-50 text-emerald-800 border border-emerald-200 px-4 py-3 rounded-xl text-xs font-medium">
              <CheckCircle2 size={16} className="text-emerald-600 shrink-0" />
              <span>All library media categorized</span>
            </div>
          )}
        </div>
      </div>

      {/* People / Faces Section */}
      <div className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h3 className="text-xs font-semibold text-stone-500 uppercase tracking-wider pl-1">
            People & Individuals
          </h3>
          <span className="text-[11px] font-mono text-stone-400">
            {Object.keys(faceGroups).length} identified clusters
          </span>
        </div>

        {scannedCount === 0 ? (
          <div className="flex flex-col items-center justify-center p-12 bg-white border border-stone-200 rounded-2xl text-center shadow-xs">
            <User size={32} className="text-stone-300 mb-2" />
            <span className="text-xs font-semibold text-stone-700">No categorized face albums yet</span>
            <p className="text-[11px] text-stone-500 max-w-xs mt-1">
              Start analysis above to generate semantic albums for recognized subjects.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {facesList.map((face) => {
              const albumPhotos = faceGroups[face] || [];
              if (albumPhotos.length === 0) return null;

              const isScenic = face === "No Face";
              const displayName = isScenic ? "Scenic Landscapes" : face;
              const displayIcon = isScenic ? <Trees size={14} /> : <User size={14} />;

              const videoCount = albumPhotos.filter((p) => p.durationSeconds > 0).length;
              const photoCount = albumPhotos.length - videoCount;

              return (
                <div
                  key={face}
                  onClick={() => onAlbumClick(face)}
                  className="bg-white border border-stone-200 rounded-2xl overflow-hidden cursor-pointer hover:border-stone-400 hover:shadow-sm transition group flex flex-col"
                >
                  <div className="relative aspect-4/3 w-full bg-stone-100 overflow-hidden">
                    <img
                      src={`/images/${albumPhotos[0].resourceName}.jpg`}
                      alt={displayName}
                      referrerPolicy="no-referrer"
                      className="w-full h-full object-cover group-hover:scale-105 transition duration-300"
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-stone-950/70 via-transparent to-transparent flex items-end p-3 text-white">
                      <div className="flex items-center gap-1.5 text-xs font-semibold">
                        {displayIcon}
                        <span>{displayName}</span>
                      </div>
                    </div>
                  </div>

                  <div className="p-3 flex items-center justify-between text-xs text-stone-500 font-mono">
                    <span>
                      {photoCount} {photoCount === 1 ? "photo" : "photos"}
                      {videoCount > 0 ? `, ${videoCount} vid` : ""}
                    </span>
                    <ChevronRight size={14} className="text-stone-400 group-hover:translate-x-0.5 transition-transform" />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Semantic Scenes & Environments Section */}
      {scannedCount > 0 && Object.keys(sceneGroups).length > 0 && (
        <div className="flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-semibold text-stone-500 uppercase tracking-wider pl-1">
              Detected Environments & Scenes
            </h3>
            <span className="text-[11px] font-mono text-stone-400">
              {Object.keys(sceneGroups).length} scene types
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {Object.entries(sceneGroups).map(([envName, envPhotos]) => (
              <div
                key={envName}
                onClick={() => onAlbumClick(envPhotos[0].faceTag || "No Face")}
                className="flex items-center gap-3 p-3 bg-white border border-stone-200 rounded-xl hover:bg-stone-50 cursor-pointer transition"
              >
                <div className="w-12 h-12 rounded-lg overflow-hidden shrink-0 bg-stone-100 border border-stone-200">
                  <img
                    src={`/images/${envPhotos[0].resourceName}.jpg`}
                    alt={envName}
                    referrerPolicy="no-referrer"
                    className="w-full h-full object-cover"
                  />
                </div>
                <div className="flex-1 min-w-0">
                  <h4 className="text-xs font-semibold text-stone-900 truncate">{envName}</h4>
                  <span className="text-[10px] font-mono text-stone-500 mt-0.5 block">
                    {envPhotos.length} {envPhotos.length === 1 ? "capture" : "captures"}
                  </span>
                </div>
                <ChevronRight size={14} className="text-stone-400" />
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
