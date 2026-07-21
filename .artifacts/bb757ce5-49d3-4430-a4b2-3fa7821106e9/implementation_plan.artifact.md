# Implementation Plan - Fix PDF Viewer Dependency Resolution

The project is failing to resolve `com.github.barteksc.pdfviewer:pdfviewer:3.2.0-beta.1` because the original coordinates were likely hosted on JCenter, which is no longer available. This library is now primarily accessed via JitPack, which uses different coordinates.

## Proposed Changes

### Build Configuration

#### [MODIFY] [app/build.gradle](file:///Users/will/AndroidStudioProjects/mercaderistas/app/build.gradle)
- Update the PDF viewer dependency to use JitPack-compatible coordinates: `com.github.barteksc:AndroidPdfViewer:3.2.0-beta.1`.

#### [MODIFY] [gradle.properties](file:///Users/will/AndroidStudioProjects/mercaderistas/gradle.properties)
- Enable `android.enableJetifier=true` and `android.useAndroidX=true` to ensure compatibility with the older PDF viewer library which may still depend on legacy support libraries.

## Verification Plan

### Automated Tests
- Run `gradle sync` to verify the dependency resolves correctly.
- Build the project to ensure no compilation errors occur due to the coordinate change.

### Manual Verification
- The user can verify that the `PdfViewerScreen` still functions as expected after the sync fix.
