# On-device face embedding model

## Model and runtime

Aura's initial face-feature extractor uses the OpenCV Zoo SFace quantized ONNX model:

- Model: `face_recognition_sface_2021dec_int8.onnx`
- Repository: `opencv/opencv_zoo`, `models/face_recognition_sface`
- Download size: approximately 9.9 MB
- SHA-256: `2b0e941e6f16cc048c20aee0c8e31f569118f65d702914540f7bfdc14048d78a`
- Runtime: `com.microsoft.onnxruntime:onnxruntime-android:1.32.0` (MIT)

The model directory contains an Apache License 2.0 notice and states that all files in that directory are licensed under Apache 2.0. The exact quantized file hash is pinned in Gradle, and the download task verifies it before moving the file into the app assets directory. The model itself is not committed as a binary to this repository.

**Provenance caveat:** An open OpenCV Zoo issue (opened July 22, 2026 and still open when this note was written) requests confirmation of the exact 2021 SFace checkpoint's training-data provenance and the scope of the weight license for commercial redistribution. The model directory's Apache notice is recorded here, but it is not a substitute for resolving that separate training-data question. Review the linked issue before commercial distribution or redistribution of the model weights.

The model README reports 0.9932 as the quantized-model evaluation accuracy. That is the model project's reported benchmark, not a measured accuracy result for Aura or the user's own library. OpenCV's sample threshold is not used as a production grouping threshold.

## Inference contract

- ML Kit face detection is configured to return all available landmarks.
- Faces smaller than 48 pixels on either bounding-box edge are not embedded.
- Five landmarks are similarity-aligned to the SFace 112x112 reference template.
- Aligned pixels are converted to RGB NCHW float values in the 0..255 range, matching OpenCV's SFace preprocessing.
- ONNX output is L2-normalized before persistence.
- Embeddings live in a dedicated Room table linked by face-row ID, and are deleted automatically when the face row is removed.
- The SFace model version is included in the media-analysis fingerprint so older face-only analysis records are reprocessed after upgrade.
- Model pixels, face crops and embeddings are handled locally. No cloud recognition endpoint is used.

## Product behavior and privacy

An embedding is a biometric feature, not a name or confirmed identity. This stage stores local face features only; it does not yet generate person albums or automatically merge identities.

The next milestone is a scalable, reviewable People-clustering layer with conservative suggestions and explicit user merge/split/rename actions. Person identity, recurring couple combinations and scene/setup similarity remain separate signals. A user should be able to correct false matches without changing or moving the original media.

## Source and compliance links

- OpenCV Zoo SFace model README and directory license: https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface
- Exact quantized model and SHA-256: https://huggingface.co/opencv/opencv_zoo/blob/main/models/face_recognition_sface/face_recognition_sface_2021dec_int8.onnx
- OpenCV SFace alignment and preprocessing: https://github.com/opencv/opencv/blob/4.x/modules/objdetect/src/face_recognize.cpp
- ONNX Runtime Android package and MIT license: https://central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime-android
- Open model provenance/license clarification issue: https://github.com/opencv/opencv_zoo/issues/313
