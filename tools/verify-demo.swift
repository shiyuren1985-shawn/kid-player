import Foundation
import AVFoundation
import AppKit

let url = URL(fileURLWithPath: CommandLine.arguments[1])
let asset = AVURLAsset(url: url)
let duration = try await asset.load(.duration)
let tracks = try await asset.loadTracks(withMediaType: .video)
guard let track = tracks.first else { fatalError("No video track") }
let size = try await track.load(.naturalSize)
let descriptions = try await track.load(.formatDescriptions)
let reader = try AVAssetReader(asset: asset)
let output = AVAssetReaderTrackOutput(track: track, outputSettings:[kCVPixelBufferPixelFormatTypeKey as String:kCVPixelFormatType_32BGRA])
reader.add(output)
guard reader.startReading() else { fatalError("Reader cannot start") }
var frames = 0
while let _ = output.copyNextSampleBuffer() { frames += 1 }
guard reader.status == .completed else { fatalError("Full decode failed: \(String(describing:reader.error))") }
let generator = AVAssetImageGenerator(asset:asset)
generator.appliesPreferredTrackTransform = true
let (image, _) = try await generator.image(at: CMTime(seconds:6, preferredTimescale:600))
let rep = NSBitmapImageRep(cgImage:image)
let preview = URL(fileURLWithPath:NSTemporaryDirectory()).appendingPathComponent("kidcinema-demo-qa.png")
try rep.representation(using:.png,properties:[:])!.write(to:preview)
print("Validated full decode: \(frames) frames; \(CMTimeGetSeconds(duration))s; \(size.width)x\(size.height); format \(descriptions); review frame: \(preview.path)")
