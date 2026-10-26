import express from "express";
import path from "path";
import fs from "fs";
import { createServer as createViteServer } from "vite";
import { GoogleGenAI } from "@google/genai";

// Standard TypeScript definitions for our backend
interface PhotoEntity {
  id: number;
  resourceName: string;
  title: string;
  sizeBytes: number;
  dateTaken: number;
  mimeType: string;
  durationSeconds: number;
  faceTag: string | null;
  originalContentHash: string;
  hasBackupSynced: boolean;
  backupLocation: string | null;
  currentFilter: string | null;
  attire: string | null;
  environment: string | null;
  lighting: string | null;
}

interface PhotoEditVersionEntity {
  id: number;
  photoId: number;
  versionNumber: number;
  filterName: string;
  timestamp: number;
  sizeDifferenceBytes: number;
}

// In-memory mock database state, initialized exactly like Room's checkAndPrepopulate
let photos: PhotoEntity[] = [];
let photoVersions: PhotoEditVersionEntity[] = [];
let nextPhotoId = 1;
let nextVersionId = 1;

function computeMockHash(str: string): string {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0; // Convert to 32bit integer
  }
  return `md5_${Math.abs(hash).toString(16)}`;
}

// Pre-populate data helper
function prepopulateDatabase() {
  const now = Date.now();
  const defaults = [
    {
      resourceName: "img_face_alice",
      title: "Alice's Portrait",
      sizeBytes: 2400000,
      dateTaken: now - 3600000 * 12,
      originalContentHash: computeMockHash("img_face_alice"),
      faceTag: null,
      mimeType: "image/jpeg",
      durationSeconds: 0
    },
    {
      resourceName: "img_face_bob",
      title: "Bob's Headshot",
      sizeBytes: 1850000,
      dateTaken: now - 3600000 * 10,
      originalContentHash: computeMockHash("img_face_bob"),
      faceTag: null,
      mimeType: "image/jpeg",
      durationSeconds: 0
    },
    {
      resourceName: "img_face_clara",
      title: "Clara's Headshot",
      sizeBytes: 2100000,
      dateTaken: now - 3600000 * 8,
      originalContentHash: computeMockHash("img_face_clara"),
      faceTag: null,
      mimeType: "image/jpeg",
      durationSeconds: 0
    },
    {
      resourceName: "img_scenic_mountain",
      title: "Golden Mountain Sunset",
      sizeBytes: 4500000,
      dateTaken: now - 3600000 * 6,
      originalContentHash: computeMockHash("img_scenic_mountain"),
      faceTag: null,
      mimeType: "image/jpeg",
      durationSeconds: 0
    },
    {
      resourceName: "img_scenic_lake",
      title: "Serene Pine Lake",
      sizeBytes: 3900000,
      dateTaken: now - 3600000 * 4,
      originalContentHash: computeMockHash("img_scenic_lake"),
      faceTag: null,
      mimeType: "image/jpeg",
      durationSeconds: 0
    },
    {
      resourceName: "img_scenic_mountain",
      title: "Mountain Sunset Flyby.mp4",
      sizeBytes: 15400000,
      dateTaken: now - 3600000 * 2,
      originalContentHash: computeMockHash("vid_sunset_flyby"),
      faceTag: null,
      mimeType: "video/mp4",
      durationSeconds: 24
    },
    {
      resourceName: "img_face_alice",
      title: "Alice Interview Clip.mp4",
      sizeBytes: 28600000,
      dateTaken: now - 3600000 * 1,
      originalContentHash: computeMockHash("vid_alice_interview"),
      faceTag: null,
      mimeType: "video/mp4",
      durationSeconds: 45
    },
    {
      resourceName: "img_face_alice",
      title: "Alice Park Jogging.mp4",
      sizeBytes: 11200000,
      dateTaken: now - 3600000 * 3,
      originalContentHash: computeMockHash("vid_alice_jogging"),
      faceTag: null,
      mimeType: "video/mp4",
      durationSeconds: 15
    },
    {
      resourceName: "img_face_alice",
      title: "Alice Studio Portrait.jpg",
      sizeBytes: 2800000,
      dateTaken: now - 3600000 * 5,
      originalContentHash: computeMockHash("img_alice_studio"),
      faceTag: null,
      mimeType: "image/jpeg",
      durationSeconds: 0
    },
    {
      resourceName: "img_face_bob",
      title: "Bob Coffee Vlog.mp4",
      sizeBytes: 13800000,
      dateTaken: now - 3600000 * 7,
      originalContentHash: computeMockHash("vid_bob_coffee"),
      faceTag: null,
      mimeType: "video/mp4",
      durationSeconds: 20
    },
    {
      resourceName: "img_face_bob",
      title: "Bob Office Presentation.mp4",
      sizeBytes: 34500000,
      dateTaken: now - 3600000 * 9,
      originalContentHash: computeMockHash("vid_bob_presentation"),
      faceTag: null,
      mimeType: "video/mp4",
      durationSeconds: 50
    }
  ];

  photos = defaults.map((item, idx) => ({
    ...item,
    id: nextPhotoId++,
    hasBackupSynced: false,
    backupLocation: null,
    currentFilter: null,
    attire: null,
    environment: null,
    lighting: null
  }));
}

prepopulateDatabase();

// Local simulation analysis as direct fallback from GeminiAnalyzer.kt
function simulateAnalysis(resourceName: string, title: string) {
  const lowerResName = resourceName.toLowerCase();
  const lowerTitle = title.toLowerCase();

  if (lowerResName.includes("alice") || lowerTitle.includes("alice")) {
    if (lowerTitle.includes("interview") || lowerTitle.includes("studio")) {
      return {
        personName: "Alice",
        description: "Alice wearing a formal dark blazer in a professional media studio with cool ring lighting.",
        detectedFace: true,
        attire: "Formal Dark Blazer",
        environment: "Professional Media Studio",
        lighting: "Cool Ring Lighting"
      };
    } else if (lowerTitle.includes("jogging") || lowerTitle.includes("park") || lowerTitle.includes("run")) {
      return {
        personName: "Alice",
        description: "Alice in sporty casual wear, jogging in an outdoor sunny park under natural daylight.",
        detectedFace: true,
        attire: "Casual Sportswear",
        environment: "Outdoor Sunny Park",
        lighting: "Natural Bright Daylight"
      };
    } else {
      return {
        personName: "Alice",
        description: "Vibrant portrait of Alice with stylish glasses, light-brown wavy hair in outdoor natural light.",
        detectedFace: true,
        attire: "White Shirt & Glasses",
        environment: "Outdoor Sunny Park",
        lighting: "Natural Bright Daylight"
      };
    }
  } else if (lowerResName.includes("bob") || lowerTitle.includes("bob")) {
    if (lowerTitle.includes("office") || lowerTitle.includes("presentation") || lowerTitle.includes("meeting")) {
      return {
        personName: "Bob",
        description: "Bob presenting confidently in a formal business suit at a modern office conference room.",
        detectedFace: true,
        attire: "Formal Business Suit",
        environment: "Modern Office Conference Room",
        lighting: "Cool Fluorescent Overhead Light"
      };
    } else {
      return {
        personName: "Bob",
        description: "Close-up portrait of Bob smiling warmly with a short dark beard and dark curly hair in a cozy living room.",
        detectedFace: true,
        attire: "Black Casual T-Shirt",
        environment: "Indoor Cozy Living Room",
        lighting: "Warm Ambient Light"
      };
    }
  } else if (lowerResName.includes("clara") || lowerTitle.includes("clara")) {
    return {
      personName: "Clara",
      description: "Elegant portrait of Clara smiling with short silver hair and a lovely blue scarf in a misty autumn forest.",
      detectedFace: true,
      attire: "Blue Woolen Scarf",
      environment: "Misty Autumn Forest",
      lighting: "Overcast Soft Daylight"
    };
  } else if (lowerResName.includes("mountain") || lowerTitle.includes("mountain")) {
    return {
      personName: null,
      description: "Spectacular view of snow-capped mountain peaks bathed in warm golden sunset light.",
      detectedFace: false,
      attire: null,
      environment: "Mountain Peak",
      lighting: "Golden Hour Sunset"
    };
  } else if (lowerResName.includes("lake") || lowerTitle.includes("lake")) {
    return {
      personName: null,
      description: "Serene alpine lake reflecting dense pine forests under a misty morning sky.",
      detectedFace: false,
      attire: null,
      environment: "Alpine Lake",
      lighting: "Overcast Soft Daylight"
    };
  } else {
    return {
      personName: null,
      description: "A clean digital capture stored in the media catalog.",
      detectedFace: false,
      attire: null,
      environment: "Unknown Setting",
      lighting: "Ambient Light"
    };
  }
}

// Background Task States
let isScanning = false;
let scanProgress = 0;

let isSyncing = false;
let syncProgress = 0;
let syncCurrent = 0;
let syncTotal = 0;
let syncActiveCloud = "";

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // API Endpoint: Get all photos
  app.get("/api/photos", (req, res) => {
    res.json(photos);
  });

  // API Endpoint: Get versions for a photo
  app.get("/api/photos/:id/versions", (req, res) => {
    const photoId = parseInt(req.params.id);
    const versions = photoVersions
      .filter((v) => v.photoId === photoId)
      .sort((a, b) => b.versionNumber - a.versionNumber); // desc
    res.json(versions);
  });

  // API Endpoint: Apply filter
  app.post("/api/photos/:id/filter", (req, res) => {
    const photoId = parseInt(req.params.id);
    const { filterName } = req.body;

    const photoIdx = photos.findIndex((p) => p.id === photoId);
    if (photoIdx === -1) {
      return res.status(404).json({ error: "Photo not found" });
    }

    const currentVersions = photoVersions.filter((v) => v.photoId === photoId);
    const nextVersionNum = currentVersions.length + 1;

    const newVersion: PhotoEditVersionEntity = {
      id: nextVersionId++,
      photoId,
      versionNumber: nextVersionNum,
      filterName,
      timestamp: Date.now(),
      sizeDifferenceBytes: 0
    };

    photoVersions.push(newVersion);
    photos[photoIdx].currentFilter = filterName;

    res.json({ photo: photos[photoIdx], version: newVersion });
  });

  // API Endpoint: Revert to version
  app.post("/api/photos/:id/revert-version", (req, res) => {
    const photoId = parseInt(req.params.id);
    const { versionNumber } = req.body;

    const photoIdx = photos.findIndex((p) => p.id === photoId);
    if (photoIdx === -1) {
      return res.status(404).json({ error: "Photo not found" });
    }

    // Delete versions higher than selected
    photoVersions = photoVersions.filter(
      (v) => !(v.photoId === photoId && v.versionNumber > versionNumber)
    );

    // Find filter name for the target version
    const targetVersion = photoVersions.find(
      (v) => v.photoId === photoId && v.versionNumber === versionNumber
    );

    photos[photoIdx].currentFilter = targetVersion ? targetVersion.filterName : null;

    res.json(photos[photoIdx]);
  });

  // API Endpoint: Revert to original
  app.post("/api/photos/:id/revert-original", (req, res) => {
    const photoId = parseInt(req.params.id);

    const photoIdx = photos.findIndex((p) => p.id === photoId);
    if (photoIdx === -1) {
      return res.status(404).json({ error: "Photo not found" });
    }

    // Clear version history
    photoVersions = photoVersions.filter((v) => v.photoId !== photoId);
    photos[photoIdx].currentFilter = null;

    res.json(photos[photoIdx]);
  });

  // API Endpoint: Delete photo
  app.delete("/api/photos/:id", (req, res) => {
    const photoId = parseInt(req.params.id);

    const photoIdx = photos.findIndex((p) => p.id === photoId);
    if (photoIdx === -1) {
      return res.status(404).json({ error: "Photo not found" });
    }

    photos.splice(photoIdx, 1);
    photoVersions = photoVersions.filter((v) => v.photoId !== photoId);

    res.json({ success: true });
  });

  // API Endpoint: Import photo with Deduplication Protection Check
  app.post("/api/photos/import", (req, res) => {
    const {
      resourceName,
      title,
      sizeBytes,
      faceTag,
      mimeType,
      durationSeconds,
      attire,
      environment,
      lighting
    } = req.body;

    // Compute unique hash based on fields
    const hash = computeMockHash(`${resourceName}_${mimeType || "image/jpeg"}_${durationSeconds || 0}_${title}`);

    // Check for duplicates
    const duplicate = photos.find((p) => p.originalContentHash === hash);
    if (duplicate) {
      return res.json({ status: "duplicate", duplicateOf: duplicate });
    }

    const newPhoto: PhotoEntity = {
      id: nextPhotoId++,
      resourceName,
      title,
      sizeBytes: parseInt(sizeBytes) || 2000000,
      dateTaken: Date.now(),
      mimeType: mimeType || "image/jpeg",
      durationSeconds: parseInt(durationSeconds) || 0,
      faceTag: faceTag || null,
      originalContentHash: hash,
      hasBackupSynced: false,
      backupLocation: null,
      currentFilter: null,
      attire: attire || null,
      environment: environment || null,
      lighting: lighting || null
    };

    photos.unshift(newPhoto); // add to top
    res.json({ status: "success", photo: newPhoto });
  });

  // API Endpoint: Face group scanning status
  app.get("/api/photos/scan-status", (req, res) => {
    res.json({ isScanning, scanProgress });
  });

  // API Endpoint: Trigger Face group scanning (Gemini + Simulated fallback)
  app.post("/api/photos/scan", async (req, res) => {
    if (isScanning) {
      return res.status(400).json({ error: "Scan already in progress" });
    }

    const unscanned = photos.filter((p) => p.faceTag === null);
    if (unscanned.length === 0) {
      return res.json({ message: "All files already scanned", photos });
    }

    isScanning = true;
    scanProgress = 0;

    res.json({ message: "Scan started", total: unscanned.length });

    // Run scanning in background
    (async () => {
      try {
        const apiKey = process.env.GEMINI_API_KEY;
        const total = unscanned.length;

        // Initialize Gemini client if key is available
        let ai: GoogleGenAI | null = null;
        if (apiKey && apiKey !== "MY_GEMINI_API_KEY" && !apiKey.includes("PLACEHOLDER") && apiKey.trim() !== "") {
          try {
            ai = new GoogleGenAI({
              apiKey: apiKey,
              httpOptions: { headers: { "User-Agent": "aistudio-build" } }
            });
            console.log("Initialized server-side Gemini client for Gallery analysis.");
          } catch (initErr) {
            console.error("Failed to initialize Gemini Client, falling back to simulated analysis", initErr);
          }
        }

        for (let i = 0; i < total; i++) {
          const photo = unscanned[i];
          const photoIdx = photos.findIndex((p) => p.id === photo.id);

          if (photoIdx !== -1) {
            let result: {
              personName: string | null;
              description: string;
              detectedFace: boolean;
              attire: string | null;
              environment: string | null;
              lighting: string | null;
            } | null = null;

            // Attempt Gemini analysis if client is active
            if (ai) {
              try {
                // Read local file as base64 to send to Gemini
                const ext = photo.resourceName.includes("mountain") || photo.resourceName.includes("lake") ? "jpg" : "jpg";
                const filePath = path.join(process.cwd(), "public", "images", `${photo.resourceName}.jpg`);
                
                if (fs.existsSync(filePath)) {
                  const base64Data = fs.readFileSync(filePath).toString("base64");
                  const imagePart = {
                    inlineData: {
                      mimeType: "image/jpeg",
                      data: base64Data
                    }
                  };

                  const prompt = `
                    Analyze this photo. Return a JSON object with this exact format:
                    {
                      "personName": "Alice" | "Bob" | "Clara" | "Unknown",
                      "detectedFace": true | false,
                      "description": "A brief, elegant 1-sentence description of the image.",
                      "attire": "A 2-4 word description of the person's clothing style and colors (e.g. 'Formal dark blazer', 'White Shirt & Glasses', 'Black Casual T-Shirt', 'Casual Sportswear'). Set to null if no person is detected.",
                      "environment": "A 2-4 word description of the scene background/location (e.g. 'Outdoor Sunny Park', 'Professional Media Studio', 'Indoor Cozy Living Room', 'Modern Office Conference Room'). Set to null if background is plain or undetectable.",
                      "lighting": "A 2-3 word description of the lighting condition (e.g. 'Natural Bright Daylight', 'Cool Ring Lighting', 'Warm Ambient Light', 'Cool Fluorescent Overhead Light'). Set to null if undetectable."
                    }
                    
                    Matching guidelines for face identification:
                    - If the person has stylish glasses, wavy light-brown hair, and outdoor natural light, name them "Alice".
                    - If the person has a short dark beard and dark curly hair, name them "Bob".
                    - If the person has short silver/grey hair, a warm smile, and a blue scarf, name them "Clara".
                    - If no face is visible, or it doesn't match these three, set "detectedFace" to false and "personName" to "Unknown".
                  `.trim();

                  const response = await ai.models.generateContent({
                    model: "gemini-3.5-flash",
                    contents: { parts: [imagePart, { text: prompt }] },
                    config: {
                      responseMimeType: "application/json"
                    }
                  });

                  if (response && response.text) {
                    const parsed = JSON.parse(response.text.trim());
                    result = {
                      personName: parsed.personName === "Unknown" || !parsed.personName ? null : parsed.personName,
                      description: parsed.description || "Analyzed image",
                      detectedFace: !!parsed.detectedFace,
                      attire: parsed.attire || null,
                      environment: parsed.environment || null,
                      lighting: parsed.lighting || null
                    };
                    console.log("Real Gemini response success for", photo.title, ":", result);
                  }
                }
              } catch (geminiErr) {
                console.error("Gemini API call failed, falling back to local simulation:", geminiErr);
              }
            }

            // Fallback to local simulation if Gemini didn't complete or wasn't configured
            if (!result) {
              result = simulateAnalysis(photo.resourceName, photo.title);
              // Add a small artificial delay of 600ms per photo to make progress visible and smooth
              await new Promise((resolve) => setTimeout(resolve, 600));
            }

            // Update photo entry in DB
            photos[photoIdx] = {
              ...photos[photoIdx],
              faceTag: result.personName || "No Face",
              title: (photo.title.includes("Portrait") || photo.title.includes("Headshot")) && result.personName
                ? `${result.personName}'s AI Portrait`
                : photo.title,
              attire: result.attire,
              environment: result.environment,
              lighting: result.lighting
            };
          }

          scanProgress = (i + 1) / total;
        }
      } catch (err) {
        console.error("Face scanning failed:", err);
      } finally {
        isScanning = false;
        scanProgress = 1.0;
      }
    })();
  });

  // API Endpoint: Sync status
  app.get("/api/photos/sync-status", (req, res) => {
    res.json({ isSyncing, syncProgress, syncCurrent, syncTotal, syncActiveCloud });
  });

  // API Endpoint: Trigger Cloud Backup sync (simulated)
  app.post("/api/photos/sync", (req, res) => {
    if (isSyncing) {
      return res.status(400).json({ error: "Sync already in progress" });
    }

    const { cloudAppName } = req.body;
    const unsynced = photos.filter((p) => !p.hasBackupSynced);
    if (unsynced.length === 0) {
      return res.json({ message: "All photos already synced", photos });
    }

    isSyncing = true;
    syncProgress = 0;
    syncCurrent = 0;
    syncTotal = unsynced.length;
    syncActiveCloud = cloudAppName || "AuraCloud Storage";

    res.json({ message: "Sync started", total: unsynced.length });

    // Run syncing in background
    (async () => {
      try {
        for (let i = 0; i < unsynced.length; i++) {
          const photo = unsynced[i];
          const photoIdx = photos.findIndex((p) => p.id === photo.id);

          // Artificial delay of 800ms per file to simulate cloud upload bandwidth
          await new Promise((resolve) => setTimeout(resolve, 800));

          if (photoIdx !== -1) {
            photos[photoIdx].hasBackupSynced = true;
            photos[photoIdx].backupLocation = syncActiveCloud;
          }

          syncCurrent = i + 1;
          syncProgress = syncCurrent / syncTotal;
        }
      } catch (err) {
        console.error("Cloud backup syncing failed:", err);
      } finally {
        isSyncing = false;
        syncProgress = 1.0;
      }
    })();
  });

  // Integrate Vite for development, or serve compiled files for production
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa"
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
