import React, { useState, useEffect, useRef, useMemo } from "react";
import {
  X,
  Play,
  Pause,
  Trash2,
  Undo2,
  CheckCircle2,
  VolumeX,
  Volume2,
  HardDrive,
  Cpu,
  BadgeCheck,
  FileText,
  Sliders,
  Sparkles,
  History,
  Heart,
  Download,
  ChevronLeft,
  ChevronRight,
  Copy,
  Check,
  Camera,
  Maximize2
} from "lucide-react";
import { PhotoEntity, PhotoEditVersionEntity, ImageAdjustments } from "../types";

interface PhotoDetailsProps {
  photo: PhotoEntity;
  versions: PhotoEditVersionEntity[];
  onDismiss: () => void;
  onApplyFilter: (filter: string) => void;
  onApplyAdjustments: (adj: ImageAdjustments) => void;
  onRevertOriginal: () => void;
  onRevertVersion: (v: PhotoEditVersionEntity) => void;
  onDelete: () => void;
  onToggleFavorite: () => void;
  onNavigatePrev?: () => void;
  onNavigateNext?: () => void;
  hasPrev?: boolean;
  hasNext?: boolean;
}

export function PhotoDetails({
  photo,
  versions,
  onDismiss,
  onApplyFilter,
  onApplyAdjustments,
  onRevertOriginal,
  onRevertVersion,
  onDelete,
  onToggleFavorite,
  onNavigatePrev,
  onNavigateNext,
  hasPrev,
  hasNext
}: PhotoDetailsProps) {
  const isVideo = photo.durationSeconds > 0 || photo.mimeType.startsWith("video/");

  // Video playback simulation states
  const [isPlaying, setIsPlaying] = useState(false);
  const [videoProgress, setVideoProgress] = useState(0);
  const [isMuted, setIsMuted] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);
  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const totalSeconds = isVideo ? photo.durationSeconds : 15;

  // Active studio sidebar tab
  const [activeTab, setActiveTab] = useState<"presets" | "adjust" | "history">("presets");

  // Fine tuning adjustments state (local copy for live real-time slider manipulation)
  const [adjustments, setAdjustments] = useState<ImageAdjustments>(
    photo.adjustments || { brightness: 0, contrast: 0, saturation: 0, warmth: 0 }
  );

  // Sync when photo changes
  useEffect(() => {
    setAdjustments(
      photo.adjustments || { brightness: 0, contrast: 0, saturation: 0, warmth: 0 }
    );
  }, [photo]);

  // "Hold to Compare Original" state
  const [isComparingOriginal, setIsComparingOriginal] = useState(false);

  // Hash copied state
  const [copiedHash, setCopiedHash] = useState(false);

  // Video simulation loop
  useEffect(() => {
    if (isPlaying) {
      timerRef.current = setInterval(() => {
        setVideoProgress((prev) => {
          const step = (0.1 * playbackSpeed) / totalSeconds;
          if (prev + step >= 1) return 0;
          return prev + step;
        });
      }, 100);
    } else if (timerRef.current) {
      clearInterval(timerRef.current);
    }
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isPlaying, playbackSpeed, totalSeconds]);

  // Keyboard navigation & shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onDismiss();
      } else if (e.key === "ArrowLeft" && hasPrev && onNavigatePrev) {
        onNavigatePrev();
      } else if (e.key === "ArrowRight" && hasNext && onNavigateNext) {
        onNavigateNext();
      } else if (e.key === " " && isVideo) {
        e.preventDefault();
        setIsPlaying((p) => !p);
      } else if (e.key.toLowerCase() === "o") {
        setIsComparingOriginal(true);
      }
    };

    const handleKeyUp = (e: KeyboardEvent) => {
      if (e.key.toLowerCase() === "o") {
        setIsComparingOriginal(false);
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    window.addEventListener("keyup", handleKeyUp);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("keyup", handleKeyUp);
    };
  }, [hasPrev, hasNext, onDismiss, onNavigatePrev, onNavigateNext, isVideo]);

  const formatDuration = (sec: number) => {
    const mins = Math.floor(sec / 60);
    const secs = Math.floor(sec % 60);
    return `${mins}:${secs < 10 ? "0" : ""}${secs}`;
  };

  const formatBytes = (bytes: number) => {
    if (bytes <= 0) return "0 B";
    const units = ["B", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
  };

  // Base preset filter CSS
  const getPresetCSS = (filterName: string | null) => {
    switch (filterName) {
      case "Vintage":
        return "sepia(0.5) brightness(0.95) contrast(1.1) saturate(1.2)";
      case "Mono":
        return "grayscale(1) contrast(1.25) brightness(0.95)";
      case "Warm Glow":
        return "sepia(0.25) brightness(1.08) saturate(1.25) contrast(1.05)";
      case "Cool Mist":
        return "contrast(0.95) saturate(0.85) brightness(1.05) hue-rotate(15deg)";
      case "Cyberpunk":
        return "hue-rotate(60deg) saturate(1.9) brightness(1.05) contrast(1.15)";
      default:
        return "";
    }
  };

  // Compute composite CSS filter including presets + fine sliders
  const compositeFilterStyle = useMemo(() => {
    if (isComparingOriginal) return "none";

    const preset = getPresetCSS(photo.currentFilter);
    const b = 1 + adjustments.brightness / 100;
    const c = 1 + adjustments.contrast / 100;
    const s = 1 + adjustments.saturation / 100;
    const w = adjustments.warmth; // warmth as sepia + hue-rotate
    const warmthCSS =
      w > 0
        ? `sepia(${w * 0.4}%) hue-rotate(${w * -0.2}deg)`
        : w < 0
        ? `hue-rotate(${Math.abs(w) * 0.25}deg)`
        : "";

    return `${preset} brightness(${b}) contrast(${c}) saturate(${s}) ${warmthCSS}`.trim() || "none";
  }, [photo.currentFilter, adjustments, isComparingOriginal]);

  // Export current photo with canvas
  const handleExport = () => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.src = `/images/${photo.resourceName}.jpg`;
    img.onload = () => {
      const canvas = document.createElement("canvas");
      canvas.width = img.naturalWidth || 1920;
      canvas.height = img.naturalHeight || 1080;
      const ctx = canvas.getContext("2d");
      if (!ctx) return;

      ctx.filter = compositeFilterStyle;
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

      const a = document.createElement("a");
      a.download = `Aura_${photo.title.replace(/\.[^/.]+$/, "")}_edited.jpg`;
      a.href = canvas.toDataURL("image/jpeg", 0.95);
      a.click();
    };
  };

  const handleCopyHash = () => {
    navigator.clipboard.writeText(photo.originalContentHash);
    setCopiedHash(true);
    setTimeout(() => setCopiedHash(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-zinc-950 text-zinc-100 overflow-y-auto">
      {/* Top Header Bar */}
      <header className="flex items-center justify-between px-4 sm:px-6 py-3 border-b border-zinc-800/80 bg-zinc-900/95 backdrop-blur-md sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <button
            onClick={onDismiss}
            className="p-2 text-zinc-400 hover:text-white rounded-lg hover:bg-zinc-800 transition"
            title="Close workbench (Esc)"
          >
            <X size={20} />
          </button>
          <div className="flex items-center gap-2 text-xs font-mono text-zinc-400">
            <span className="text-zinc-200 font-semibold truncate max-w-[200px] sm:max-w-xs">
              {photo.title}
            </span>
            <span>·</span>
            <span>{photo.dimensions ? `${photo.dimensions.width}×${photo.dimensions.height}` : "Hi-Res"}</span>
            <span>·</span>
            <span>{formatBytes(photo.sizeBytes)}</span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Favorite Toggle Button */}
          <button
            onClick={onToggleFavorite}
            className={`p-2 rounded-lg transition ${
              photo.isFavorite
                ? "text-rose-500 bg-rose-500/10 hover:bg-rose-500/20"
                : "text-zinc-400 hover:text-white hover:bg-zinc-800"
            }`}
            title={photo.isFavorite ? "Remove from Favorites" : "Add to Favorites"}
          >
            <Heart size={18} fill={photo.isFavorite ? "currentColor" : "none"} />
          </button>

          {/* Export Button */}
          <button
            onClick={handleExport}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded-lg text-xs font-medium transition cursor-pointer"
            title="Export edited file"
          >
            <Download size={14} />
            <span className="hidden sm:inline">Export</span>
          </button>

          {/* Delete Button */}
          <button
            onClick={onDelete}
            className="p-2 text-zinc-400 hover:text-rose-400 rounded-lg hover:bg-rose-950/30 transition"
            title="Delete photo"
          >
            <Trash2 size={18} />
          </button>
        </div>
      </header>

      {/* Main Workbench Body */}
      <div className="max-w-7xl mx-auto w-full px-4 sm:px-6 py-6 flex-1 grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Stage: Viewer / Player */}
        <div className="lg:col-span-8 flex flex-col gap-4">
          <div className="relative w-full aspect-[4/3] md:aspect-[16/10] bg-zinc-900 rounded-2xl overflow-hidden shadow-2xl border border-zinc-850 flex items-center justify-center group select-none">
            {/* The Image with Live Composite Filter */}
            <img
              src={`/images/${photo.resourceName}.jpg`}
              alt={photo.title}
              referrerPolicy="no-referrer"
              style={{ filter: compositeFilterStyle }}
              className="w-full h-full object-contain transition-all duration-150"
            />

            {/* Navigation Arrows for Previous / Next */}
            {hasPrev && onNavigatePrev && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onNavigatePrev();
                }}
                className="absolute left-3 p-2.5 rounded-full bg-zinc-950/70 text-zinc-300 hover:text-white hover:bg-zinc-900 transition opacity-0 group-hover:opacity-100 cursor-pointer"
                title="Previous (Left Arrow)"
              >
                <ChevronLeft size={20} />
              </button>
            )}

            {hasNext && onNavigateNext && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onNavigateNext();
                }}
                className="absolute right-3 p-2.5 rounded-full bg-zinc-950/70 text-zinc-300 hover:text-white hover:bg-zinc-900 transition opacity-0 group-hover:opacity-100 cursor-pointer"
                title="Next (Right Arrow)"
              >
                <ChevronRight size={20} />
              </button>
            )}

            {/* Hold to Compare Watermark Badge */}
            {isComparingOriginal && (
              <div className="absolute top-4 left-4 bg-amber-500/90 text-zinc-950 text-[10px] font-mono font-bold px-2 py-0.5 rounded shadow">
                ORIGINAL PREVIEW (UNEDITED)
              </div>
            )}

            {/* Video Controls Overlay */}
            {isVideo && (
              <>
                <button
                  onClick={() => setIsPlaying(!isPlaying)}
                  className="absolute p-4 rounded-full bg-zinc-950/60 text-white hover:bg-zinc-950/80 hover:scale-105 transition"
                >
                  {isPlaying ? <Pause size={30} fill="white" /> : <Play size={30} fill="white" />}
                </button>

                {/* Bottom Scrub Bar */}
                <div className="absolute bottom-0 inset-x-0 p-3 flex flex-col gap-1.5 bg-gradient-to-t from-zinc-950 via-zinc-950/70 to-transparent">
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.001"
                    value={videoProgress}
                    onChange={(e) => setVideoProgress(parseFloat(e.target.value))}
                    className="w-full h-1 bg-zinc-700 rounded-lg appearance-none cursor-pointer accent-indigo-500"
                  />
                  <div className="flex items-center justify-between text-xs text-zinc-300 font-mono">
                    <span>
                      {formatDuration(videoProgress * totalSeconds)} / {formatDuration(totalSeconds)}
                    </span>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() =>
                          setPlaybackSpeed((p) => (p === 1 ? 1.5 : p === 1.5 ? 2 : p === 2 ? 0.5 : 1))
                        }
                        className="bg-zinc-800 px-1.5 py-0.5 rounded text-[10px] font-bold"
                      >
                        {playbackSpeed}x
                      </button>
                      <button onClick={() => setIsMuted(!isMuted)}>
                        {isMuted ? <VolumeX size={14} /> : <Volume2 size={14} />}
                      </button>
                    </div>
                  </div>
                </div>
              </>
            )}
          </div>

          {/* Under-Stage Quick Bar: Hold to Compare & Navigation Indicators */}
          <div className="flex items-center justify-between text-xs text-zinc-400 px-1">
            <button
              onMouseDown={() => setIsComparingOriginal(true)}
              onMouseUp={() => setIsComparingOriginal(false)}
              onMouseLeave={() => setIsComparingOriginal(false)}
              onTouchStart={() => setIsComparingOriginal(true)}
              onTouchEnd={() => setIsComparingOriginal(false)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-zinc-900 border border-zinc-800 text-zinc-300 hover:text-white transition select-none cursor-pointer"
            >
              <EyeToggleIcon size={14} />
              <span>Hold to Compare Original (or press 'O')</span>
            </button>

            <span className="font-mono text-[11px] text-zinc-500">
              {photo.cameraModel || "Standard Capture"}
            </span>
          </div>

          {/* EXIF Technical Details Card */}
          <div className="bg-zinc-900/80 border border-zinc-850 rounded-2xl p-5 flex flex-col gap-4">
            <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
              <div className="flex items-center gap-2 text-zinc-300">
                <Camera size={16} className="text-zinc-400" />
                <h3 className="text-xs font-semibold uppercase tracking-wider">
                  Camera & Exposure Parameters
                </h3>
              </div>
              <span className="text-[11px] font-mono text-zinc-500">
                {new Date(photo.dateTaken).toLocaleString()}
              </span>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
              <div>
                <span className="text-zinc-500 block text-[10px] uppercase font-mono">Camera</span>
                <span className="text-zinc-200 font-medium">{photo.cameraModel || "Custom Sensor"}</span>
              </div>
              <div>
                <span className="text-zinc-500 block text-[10px] uppercase font-mono">Lens</span>
                <span className="text-zinc-200 font-medium">{photo.lens || "50mm Prime"}</span>
              </div>
              <div>
                <span className="text-zinc-500 block text-[10px] uppercase font-mono">Focal Length</span>
                <span className="text-zinc-200 font-mono font-medium">{photo.focalLength || "50mm"}</span>
              </div>
              <div>
                <span className="text-zinc-500 block text-[10px] uppercase font-mono">Aperture / ISO</span>
                <span className="text-zinc-200 font-mono font-medium">
                  {photo.aperture || "f/1.8"} · ISO {photo.iso || 100}
                </span>
              </div>
            </div>

            {/* AI Semantic Metadata (Zero-pill clean text) */}
            <div className="pt-3 border-t border-zinc-800 flex flex-col gap-1.5 text-xs text-zinc-400">
              <div className="flex items-center gap-2 font-mono text-[11px]">
                <Cpu size={13} className="text-zinc-500" />
                <span className="text-zinc-300 font-medium">
                  Face Detection: {photo.faceTag || "Unscanned"}
                </span>
                <span>·</span>
                <span>Scene: {photo.environment || "General"}</span>
                <span>·</span>
                <span>Light: {photo.lighting || "Ambient"}</span>
              </div>

              <div className="flex items-center gap-2 font-mono text-[11px] text-zinc-500 mt-1">
                <FileText size={13} />
                <span className="truncate">Hash: {photo.originalContentHash}</span>
                <button
                  onClick={handleCopyHash}
                  className="p-1 hover:text-zinc-300 rounded transition"
                  title="Copy MD5 Content Hash"
                >
                  {copiedHash ? <Check size={12} className="text-emerald-400" /> : <Copy size={12} />}
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Right Stage: Studio Sidebar (Presets / Fine Tuning / Delta History) */}
        <div className="lg:col-span-4 flex flex-col gap-4">
          {/* Segmented Control Tabs */}
          <div className="flex items-center bg-zinc-900 p-1 rounded-xl border border-zinc-800">
            <button
              onClick={() => setActiveTab("presets")}
              className={`flex-1 flex items-center justify-center gap-1.5 py-1.5 text-xs font-semibold rounded-lg transition ${
                activeTab === "presets"
                  ? "bg-zinc-800 text-white shadow-sm"
                  : "text-zinc-400 hover:text-zinc-200"
              }`}
            >
              <Sparkles size={13} />
              Presets
            </button>
            <button
              onClick={() => setActiveTab("adjust")}
              className={`flex-1 flex items-center justify-center gap-1.5 py-1.5 text-xs font-semibold rounded-lg transition ${
                activeTab === "adjust"
                  ? "bg-zinc-800 text-white shadow-sm"
                  : "text-zinc-400 hover:text-zinc-200"
              }`}
            >
              <Sliders size={13} />
              Fine Tune
            </button>
            <button
              onClick={() => setActiveTab("history")}
              className={`flex-1 flex items-center justify-center gap-1.5 py-1.5 text-xs font-semibold rounded-lg transition ${
                activeTab === "history"
                  ? "bg-zinc-800 text-white shadow-sm"
                  : "text-zinc-400 hover:text-zinc-200"
              }`}
            >
              <History size={13} />
              History ({versions.length})
            </button>
          </div>

          {/* TAB 1: PRESET LUT FILTERS */}
          {activeTab === "presets" && (
            <div className="bg-zinc-900 border border-zinc-850 rounded-2xl p-5 flex flex-col gap-4">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-semibold text-zinc-300 uppercase tracking-wider">
                  Color Grading LUTs
                </h3>
                {photo.currentFilter && (
                  <button
                    onClick={onRevertOriginal}
                    className="text-[11px] font-medium text-zinc-400 hover:text-white transition"
                  >
                    Reset
                  </button>
                )}
              </div>

              <div className="grid grid-cols-3 gap-3">
                {[
                  { name: "Original", css: "" },
                  { name: "Vintage", css: "sepia(0.5) brightness(0.95) contrast(1.1) saturate(1.2)" },
                  { name: "Mono", css: "grayscale(1) contrast(1.25) brightness(0.95)" },
                  { name: "Warm Glow", css: "sepia(0.25) brightness(1.08) saturate(1.25) contrast(1.05)" },
                  { name: "Cool Mist", css: "contrast(0.95) saturate(0.85) brightness(1.05) hue-rotate(15deg)" },
                  { name: "Cyberpunk", css: "hue-rotate(60deg) saturate(1.9) brightness(1.05) contrast(1.15)" }
                ].map((f) => {
                  const isSelected =
                    photo.currentFilter === f.name ||
                    (photo.currentFilter === null && f.name === "Original");
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
                      className={`flex flex-col items-center gap-1.5 p-1 rounded-xl transition hover:bg-zinc-800/60 group cursor-pointer ${
                        isSelected ? "ring-2 ring-indigo-500 ring-offset-2 ring-offset-zinc-950" : ""
                      }`}
                    >
                      <div className="relative w-full aspect-square rounded-lg overflow-hidden border border-zinc-750">
                        <img
                          src={`/images/${photo.resourceName}.jpg`}
                          alt={f.name}
                          referrerPolicy="no-referrer"
                          style={{ filter: f.css }}
                          className="w-full h-full object-cover group-hover:scale-105 transition duration-300"
                        />
                      </div>
                      <span
                        className={`text-[10px] font-medium truncate ${
                          isSelected ? "text-indigo-400 font-bold" : "text-zinc-400"
                        }`}
                      >
                        {f.name}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* TAB 2: FINE TUNE SLIDERS */}
          {activeTab === "adjust" && (
            <div className="bg-zinc-900 border border-zinc-850 rounded-2xl p-5 flex flex-col gap-5">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-semibold text-zinc-300 uppercase tracking-wider">
                  Color & Tone Sliders
                </h3>
                <button
                  onClick={() => {
                    const reset = { brightness: 0, contrast: 0, saturation: 0, warmth: 0 };
                    setAdjustments(reset);
                    onApplyAdjustments(reset);
                  }}
                  className="text-[11px] font-medium text-zinc-400 hover:text-white transition cursor-pointer"
                >
                  Reset All
                </button>
              </div>

              {/* Slider 1: Brightness */}
              <div className="flex flex-col gap-1.5">
                <div className="flex justify-between text-xs">
                  <span className="text-zinc-400">Exposure / Brightness</span>
                  <span className="font-mono text-zinc-200">
                    {adjustments.brightness > 0 ? `+${adjustments.brightness}` : adjustments.brightness}
                  </span>
                </div>
                <input
                  type="range"
                  min="-100"
                  max="100"
                  value={adjustments.brightness}
                  onChange={(e) =>
                    setAdjustments({ ...adjustments, brightness: parseInt(e.target.value) })
                  }
                  className="w-full h-1.5 bg-zinc-800 rounded-lg appearance-none cursor-pointer accent-indigo-500"
                />
              </div>

              {/* Slider 2: Contrast */}
              <div className="flex flex-col gap-1.5">
                <div className="flex justify-between text-xs">
                  <span className="text-zinc-400">Contrast</span>
                  <span className="font-mono text-zinc-200">
                    {adjustments.contrast > 0 ? `+${adjustments.contrast}` : adjustments.contrast}
                  </span>
                </div>
                <input
                  type="range"
                  min="-100"
                  max="100"
                  value={adjustments.contrast}
                  onChange={(e) =>
                    setAdjustments({ ...adjustments, contrast: parseInt(e.target.value) })
                  }
                  className="w-full h-1.5 bg-zinc-800 rounded-lg appearance-none cursor-pointer accent-indigo-500"
                />
              </div>

              {/* Slider 3: Saturation */}
              <div className="flex flex-col gap-1.5">
                <div className="flex justify-between text-xs">
                  <span className="text-zinc-400">Saturation</span>
                  <span className="font-mono text-zinc-200">
                    {adjustments.saturation > 0 ? `+${adjustments.saturation}` : adjustments.saturation}
                  </span>
                </div>
                <input
                  type="range"
                  min="-100"
                  max="100"
                  value={adjustments.saturation}
                  onChange={(e) =>
                    setAdjustments({ ...adjustments, saturation: parseInt(e.target.value) })
                  }
                  className="w-full h-1.5 bg-zinc-800 rounded-lg appearance-none cursor-pointer accent-indigo-500"
                />
              </div>

              {/* Slider 4: Warmth */}
              <div className="flex flex-col gap-1.5">
                <div className="flex justify-between text-xs">
                  <span className="text-zinc-400">Color Temperature / Warmth</span>
                  <span className="font-mono text-zinc-200">
                    {adjustments.warmth > 0 ? `+${adjustments.warmth}` : adjustments.warmth}
                  </span>
                </div>
                <input
                  type="range"
                  min="-100"
                  max="100"
                  value={adjustments.warmth}
                  onChange={(e) =>
                    setAdjustments({ ...adjustments, warmth: parseInt(e.target.value) })
                  }
                  className="w-full h-1.5 bg-zinc-800 rounded-lg appearance-none cursor-pointer accent-indigo-500"
                />
              </div>

              {/* Commit changes */}
              <button
                onClick={() => onApplyAdjustments(adjustments)}
                className="w-full mt-2 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-semibold transition cursor-pointer shadow"
              >
                Save Non-Destructive Tuning Delta
              </button>
            </div>
          )}

          {/* TAB 3: DELTA VERSION HISTORY */}
          {activeTab === "history" && (
            <div className="bg-zinc-900 border border-zinc-850 rounded-2xl p-5 flex flex-col gap-4 flex-1">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-semibold text-zinc-300 uppercase tracking-wider">
                  Delta Version History
                </h3>
                {versions.length > 0 && (
                  <button
                    onClick={onRevertOriginal}
                    className="flex items-center gap-1 text-[11px] text-zinc-400 hover:text-white transition"
                  >
                    <Undo2 size={12} />
                    Reset to Original
                  </button>
                )}
              </div>

              {versions.length === 0 ? (
                <div className="flex-1 flex flex-col items-center justify-center text-center p-6 bg-zinc-950/40 rounded-xl border border-zinc-800">
                  <CheckCircle2 size={28} className="text-zinc-600 mb-2" />
                  <span className="text-xs font-semibold text-zinc-300 mb-1">Original Active</span>
                  <p className="text-[10px] text-zinc-500 max-w-xs leading-relaxed">
                    No modifications applied. Delta storage compression prevents redundant physical copies.
                  </p>
                </div>
              ) : (
                <div className="flex flex-col gap-2.5 overflow-y-auto max-h-[320px] pr-1">
                  {versions.map((version) => {
                    return (
                      <div
                        key={version.id}
                        className="flex items-center justify-between p-3 rounded-xl border border-zinc-800 bg-zinc-950/40 hover:bg-zinc-800/40 transition text-xs"
                      >
                        <div className="flex flex-col">
                          <span className="font-semibold text-zinc-200">
                            Version {version.versionNumber} ({version.filterName})
                          </span>
                          <span className="text-[10px] text-zinc-500 font-mono">
                            {new Date(version.timestamp).toLocaleTimeString()} · Delta 0 B
                          </span>
                        </div>
                        <button
                          onClick={() => onRevertVersion(version)}
                          className="px-2.5 py-1 rounded bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-[10px] font-medium transition cursor-pointer"
                        >
                          Restore
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function EyeToggleIcon({ size }: { size: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}
