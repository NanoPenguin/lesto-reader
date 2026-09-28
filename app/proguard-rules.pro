# Project-specific R8 rules. Keep this file empty unless a rule is required.

# PdfBox-Android decodes JPEG 2000 images with an optional library; Pace only reads text.
-dontwarn com.gemalto.jp2.JP2Decoder
