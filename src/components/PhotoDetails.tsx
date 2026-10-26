import React, { useState, useEffect, useRef } from "react";
import { X, Play, Pause, Trash2, Undo2, CheckCircle2, VolumeX, Volume2, HardDrive, Cpu, ShieldAlert, BadgeCheck, FileText, Settings } from "lucide-react";
import { PhotoEntity, PhotoEditVersionEntity } from "../types";

interface PhotoDetailsProps {
  photo: PhotoEntity;
  versions: PhotoEditVersionEntity[];
  onDismiss: () => void;
  onApplyFilter: (filter: string) => void;
  onRevertOriginal: () => void;
  onRevertVersion: (v: PhotoEditVersionEntity) => void;
  onDelete: () => void;
}

export function PhotoDetails({
  photo,
  versions,
  onDismiss,
  onApplyFilter,
  onRevertOriginal,
  onRevertVersion,
  onDelete
}: PhotoDetailsProps) {
  const isVideo = photo.durationSeconds > 0 || photo.mimeType.startsWith("video/");

  // Video playback simulation states
  const [isPlaying, setIsPlaying] = useState(false);
  const [videoProgress, setVideoProgress] = useState(0);
  const [isMuted, setIsMuted] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);

  const totalSeconds = isVideo ? photo.durationSeconds : 15;
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  // Video simulation loop
  useEffect(() => {
    if (isPlaying) {
      timerRef.current = setInterval(() => {
        setVideoProgress((prev) => {
          const step = (0.1 * playbackSpeed) / totalSeconds;
          if (prev + step >= 1) {
            return 0; // Loop playback
          }
          return prev + step;
        });
      }, 100);
    } else if (timerRef.current) {
      clearInterval(timerRef.current);
    }

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    };
  }, [isPlaying, playbackSpeed, totalSeconds]);

  // Format helper for video durations
  const formatDuration = (sec: number) => {
    const mins = Math.floor(sec / 60);
    const secs = Math.floor(sec % 60);
    return `${mins}:${secs < 10 ? "0" : ""}${secs}`;
  };

  // Helper for applying Tailwind LUT filter classes visually
  const getFilterClass = (filterName: string | null) => {
    switch (filterName) {
      case "Vintage":
        return "sepia brightness-90 contrast-110 saturate-120 hue-rotate-15";
      case "Mono":
        return "grayscale contrast-125 brightness-95";
      case "Warm Glow":
        return "sepia-30 brightness-110 saturate-130 contrast-105 hue-rotate-5";
      case "Cool Mist":
        return "contrast-95 saturate-85 brightness-105 hue-rotate--10 cyan-tint";
      case "Cyberpunk":
        return "hue-rotate-60 saturate-200 brightness-105 contrast-125 pink-violet-tint";
      default:
        return "";
    }
  };

  const formatBytes = (bytes: number) => {
    if (bytes <= 0) return "0 B";
    const units = ["B", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
  };

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-gray-950 text-gray-100 overflow-y-auto">
      {/* Header Toolbar */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800 bg-gray-900 sticky top-0 z-10">
        <button
          onClick={onDismiss}
          className="p-2 text-gray-400 hover:text-white rounded-full hover:bg-gray-800 transition"
        >
          <X size={22} />
        </button>
        <span className="font-display font-medium text-base tracking-wide text-gray-200">
          {isVideo ? "AI Video Studio" : "AI Photo Workbench"}
        </span>
        <button
          onClick={onDelete}
          className="p-2 text-red-400 hover:text-red-300 rounded-full hover:bg-red-950/40 transition"
          title="Delete media from gallery"
        >
          <Trash2 size={20} />
        </button>
      </div>

      <div className="max-w-4xl mx-auto w-full px-4 py-6 flex-1 grid grid-cols-1 md:grid-cols-12 gap-6">
        {/* Left Side: Preview Stage */}
        <div className="md:col-span-7 flex flex-col gap-4">
          <div className="relative w-full aspect-[4/3] md:aspect-[16/11] bg-black rounded-2xl overflow-hidden shadow-2xl border border-gray-800 flex items-center justify-center group">
            {/* The actual image with responsive LUT CSS class filter */}
            <img
              src={`/images/${photo.resourceName}.jpg`}
              alt={photo.title}
              referrerPolicy="no-referrer"
              className={`w-full h-full object-cover transition-all duration-300 ${getFilterClass(photo.currentFilter)}`}
            />

            {/* Video Overlays */}
            {isVideo && (
              <>
                {/* Vignette */}
                <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-black/30 pointer-events-none" />

                {/* Video Tag */}
                <div className="absolute top-3 left-3 flex items-center gap-1.5 bg-red-600/90 text-white text-[10px] font-bold px-2 py-1 rounded">
                  <span className={`w-1.5 h-1.5 rounded-full bg-white ${isPlaying ? "animate-pulse" : ""}`} />
                  {isPlaying ? "PLAYING" : "PAUSED"}
                </div>

                {/* LUT Overlay Label if any active */}
                {photo.currentFilter && (
                  <div className="absolute top-3 right-3 bg-black/60 backdrop-blur-sm text-gray-200 text-[10px] font-bold px-2 py-1 rounded border border-gray-700">
                    LUT ACTIVE: {photo.currentFilter}
                  </div>
                )}

                {/* Large Center Play Toggle */}
                <button
                  onClick={() => setIsPlaying(!isPlaying)}
                  className="absolute p-4 rounded-full bg-black/50 text-white hover:bg-black/70 hover:scale-105 transition duration-200"
                >
                  {isPlaying ? <Pause size={32} fill="white" /> : <Play size={32} fill="white" />}
                </button>

                {/* Bottom Custom Playback Bar */}
                <div className="absolute bottom-0 inset-x-0 p-3 flex flex-col gap-1.5 bg-gradient-to-t from-black/90 to-transparent">
                  {/* Timeline Slider */}
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.001"
                    value={videoProgress}
                    onChange={(e) => setVideoProgress(parseFloat(e.target.value))}
                    className="w-full h-1 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-brand-purple"
                  />

                  {/* Playback Settings */}
                  <div className="flex items-center justify-between text-xs text-gray-300 font-mono mt-1">
                    <div className="flex items-center gap-3">
                      <button onClick={() => setIsPlaying(!isPlaying)} className="hover:text-white">
                        {isPlaying ? <Pause size={14} /> : <Play size={14} />}
                      </button>
                      <span>
                        {formatDuration(videoProgress * totalSeconds)} / {formatDuration(totalSeconds)}
                      </span>
                    </div>

                    <div className="flex items-center gap-3">
                      {/* Playback speed multiplier */}
                      <button
                        onClick={() => {
                          setPlaybackSpeed((prev) => {
                            if (prev === 1) return 1.5;
                            if (prev === 1.5) return 2;
                            if (prev === 2) return 0.5;
                            return 1;
                          });
                        }}
                        className="font-bold hover:text-white bg-gray-800 hover:bg-gray-700 px-1.5 py-0.5 rounded transition"
                      >
                        {playbackSpeed}x
                      </button>

                      {/* Simulated Audio mute */}
                      <button onClick={() => setIsMuted(!isMuted)} className="hover:text-white">
                        {isMuted ? <VolumeX size={15} /> : <Volume2 size={15} />}
                      </button>
                    </div>
                  </div>
                </div>
              </>
            )}

            {/* Static Photo LUT overlay badge if filter applied */}
            {!isVideo && photo.currentFilter && (
              <div className="absolute top-3 right-3 bg-brand-purple/90 text-white text-xs font-semibold px-2.5 py-1 rounded-full shadow-lg">
                Filter: {photo.currentFilter}
              </div>
            )}
          </div>

          {/* Details & Specs Cards */}
          <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5 flex flex-col gap-4">
            <div>
              <h2 className="text-xl font-bold tracking-tight text-white mb-2">{photo.title}</h2>
              <div className="flex flex-wrap gap-2">
                <span className="bg-brand-purple/20 text-brand-purple-container text-[11px] font-bold px-2 py-0.5 rounded border border-brand-purple/30">
                  {isVideo ? "VIDEO CLIPS" : "HI-RES IMAGE"}
                </span>
                <span className="bg-gray-800 text-gray-300 text-[11px] font-medium px-2 py-0.5 rounded">
                  {formatBytes(photo.sizeBytes)}
                </span>
                {isVideo && (
                  <span className="bg-gray-800 text-gray-300 text-[11px] font-medium px-2 py-0.5 rounded">
                    {photo.durationSeconds}s @ 60fps
                  </span>
                )}
              </div>
            </div>

            <div className="h-px bg-gray-800" />

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs font-mono text-gray-400">
              <div className="flex flex-col gap-2">
                <div className="flex items-center gap-2">
                  <Cpu size={14} className="text-gray-500" />
                  <span>Category: <strong className="text-gray-200">{photo.faceTag || "Unscanned"}</strong></span>
                </div>
                <div className="flex items-center gap-2">
                  <FileText size={14} className="text-gray-500" />
                  <span className="truncate">Hash: <strong className="text-gray-200">{photo.originalContentHash}</strong></span>
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <div className="flex items-center gap-2">
                  <HardDrive size={14} className="text-gray-500" />
                  <span>Backup: <strong className="text-gray-200">{photo.hasBackupSynced ? `Synced (${photo.backupLocation?.split(" ")[0]})` : "Unbacked"}</strong></span>
                </div>
                {isVideo && (
                  <div className="flex items-center gap-2">
                    <Settings size={14} className="text-gray-500" />
                    <span>Codec: <strong className="text-gray-200">H.264 / AAC Stereo</strong></span>
                  </div>
                )}
              </div>
            </div>

            {/* AI Metadata Tags Row if present */}
            {(photo.attire || photo.environment || photo.lighting) && (
              <div className="mt-2 bg-gray-950/60 p-3.5 rounded-xl border border-gray-800/80">
                <div className="flex items-center gap-1.5 mb-2 text-xs font-semibold text-brand-purple-container">
                  <BadgeCheck size={14} className="text-brand-purple" />
                  <span>AI Semantic Signatures</span>
                </div>
                <div className="grid grid-cols-3 gap-2 text-center text-[10px]">
                  <div className="bg-gray-900 border border-gray-800 p-2 rounded-lg">
                    <span className="text-gray-500 block mb-0.5">Attire</span>
                    <span className="text-gray-200 font-medium">{photo.attire || "N/A"}</span>
                  </div>
                  <div className="bg-gray-900 border border-gray-800 p-2 rounded-lg">
                    <span className="text-gray-500 block mb-0.5">Environment</span>
                    <span className="text-gray-200 font-medium">{photo.environment || "N/A"}</span>
                  </div>
                  <div className="bg-gray-900 border border-gray-800 p-2 rounded-lg">
                    <span className="text-gray-500 block mb-0.5">Lighting</span>
                    <span className="text-gray-200 font-medium">{photo.lighting || "N/A"}</span>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Right Side: Non-destructive Studio & Version Control */}
        <div className="md:col-span-5 flex flex-col gap-6">
          {/* Non-destructive LUT Filter Panel */}
          <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5 flex flex-col gap-4">
            <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider">
              Apply Non-Destructive Filters
            </h3>

            <div className="grid grid-cols-3 gap-3">
              {[
                { name: "Original", css: "" },
                { name: "Vintage", css: "sepia brightness-90 contrast-110 saturate-120" },
                { name: "Mono", css: "grayscale contrast-125" },
                { name: "Warm Glow", css: "sepia-30 brightness-105 saturate-130 contrast-105" },
                { name: "Cool Mist", css: "contrast-95 saturate-80 brightness-105 hue-rotate-15" },
                { name: "Cyberpunk", css: "hue-rotate-60 saturate-200 brightness-105" }
              ].map((f) => {
                const isSelected =
                  photo.currentFilter === f.name || (photo.currentFilter === null && f.name === "Original");
                return (
                  <button
                    key={f.name}
                    onClick={() => {
                      if (f.name === "Original") {
                        onRevertOriginal();
                      } else {
                        onApplyFilter(f.name);
                      }
                    }}
                    className={`flex flex-col items-center gap-1.5 p-1 rounded-xl transition hover:bg-gray-800/50 group ${
                      isSelected ? "ring-2 ring-brand-purple ring-offset-2 ring-offset-gray-950" : ""
                    }`}
                  >
                    <div className="relative w-full aspect-square rounded-lg overflow-hidden border border-gray-700">
                      <img
                        src={`/images/${photo.resourceName}.jpg`}
                        alt={f.name}
                        referrerPolicy="no-referrer"
                        className={`w-full h-full object-cover group-hover:scale-105 transition duration-300 ${f.css}`}
                      />
                    </div>
                    <span className={`text-[10px] font-medium tracking-wide ${isSelected ? "text-brand-purple-container font-bold" : "text-gray-400"}`}>
                      {f.name}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Version Control Panel */}
          <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5 flex flex-col gap-4 flex-1">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider">
                Version History
              </h3>
              {versions.length > 0 && (
                <button
                  onClick={onRevertOriginal}
                  className="flex items-center gap-1 text-xs text-brand-purple hover:text-brand-purple-container font-medium transition"
                >
                  <Undo2 size={13} />
                  Revert Original
                </button>
              )}
            </div>

            {versions.length === 0 ? (
              <div className="flex-1 flex flex-col items-center justify-center text-center p-6 bg-gray-950/40 rounded-xl border border-gray-800/80">
                <CheckCircle2 size={32} className="text-gray-600 mb-2" />
                <span className="text-xs font-semibold text-gray-400 mb-1">
                  Active Original File
                </span>
                <p className="text-[10px] text-gray-500 max-w-xs leading-relaxed">
                  No edits are active. Delta version compression saves space (0 B additional physical space allocated).
                </p>
              </div>
            ) : (
              <div className="flex flex-col gap-3 overflow-y-auto max-h-[300px] pr-1">
                {versions.map((version) => {
                  const isActive = photo.currentFilter === version.filterName;
                  return (
                    <div
                      key={version.id}
                      className={`flex items-center justify-between p-3 rounded-xl border transition ${
                        isActive
                          ? "bg-brand-purple/10 border-brand-purple/40 text-brand-purple-container"
                          : "bg-gray-950/40 border-gray-800/80 text-gray-300"
                      }`}
                    >
                      <div>
                        <div className="flex items-center gap-1.5">
                          <span className="text-xs font-bold font-mono">
                            V{version.versionNumber}
                          </span>
                          <span className="text-xs font-semibold">
                            {version.filterName} LUT
                          </span>
                        </div>
                        <span className="text-[9px] text-gray-500 block mt-0.5 font-mono">
                          Applied {new Date(version.timestamp).toLocaleTimeString()} · Delta: 0 B
                        </span>
                      </div>

                      {isActive ? (
                        <div className="flex items-center gap-1 text-[10px] text-green-400 font-bold">
                          <CheckCircle2 size={12} className="fill-green-950" />
                          <span>Active</span>
                        </div>
                      ) : (
                        <button
                          onClick={() => onRevertVersion(version)}
                          className="bg-brand-purple hover:bg-brand-purple-container hover:text-brand-purple text-white text-[10px] font-bold px-2.5 py-1 rounded transition shadow"
                        >
                          Revert
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
