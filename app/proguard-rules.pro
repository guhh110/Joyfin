# libass-android 0.5.1 accesses these Media3 fields by reflection for MKV ASS samples/fonts.
-keepclassmembers class androidx.media3.extractor.mkv.MatroskaExtractor {
    androidx.media3.extractor.ExtractorOutput extractorOutput;
    androidx.media3.common.util.ParsableByteArray subtitleSample;
}
