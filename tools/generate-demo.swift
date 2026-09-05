import Foundation
import AVFoundation
import CoreGraphics
import CoreVideo
import AppKit

// Original procedural artwork. No network, stock footage, audio, or external assets.
let outputPath = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "app/src/main/res/raw/demo_space.mp4"
let outputURL = URL(fileURLWithPath: outputPath)
try FileManager.default.createDirectory(at: outputURL.deletingLastPathComponent(), withIntermediateDirectories: true)
guard !FileManager.default.fileExists(atPath: outputPath) else {
    fatalError("Output already exists. Choose a new output path to preserve it.")
}
let width = 960, height = 540, fps = 24, seconds = 18
let writer = try AVAssetWriter(outputURL: outputURL, fileType: .mp4)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
    AVVideoCodecKey: AVVideoCodecType.h264,
    AVVideoWidthKey: width,
    AVVideoHeightKey: height,
    AVVideoCompressionPropertiesKey: [
        AVVideoAverageBitRateKey: 1_600_000,
        AVVideoProfileLevelKey: AVVideoProfileLevelH264BaselineAutoLevel,
        AVVideoAllowFrameReorderingKey: false,
        AVVideoMaxKeyFrameIntervalKey: fps
    ]
])
input.expectsMediaDataInRealTime = false
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB,
    kCVPixelBufferWidthKey as String: width,
    kCVPixelBufferHeightKey as String: height,
    kCVPixelBufferCGImageCompatibilityKey as String: true,
    kCVPixelBufferCGBitmapContextCompatibilityKey as String: true
])
writer.add(input)
guard writer.startWriting() else { fatalError("Cannot start writer: \(String(describing: writer.error))") }
writer.startSession(atSourceTime: .zero)
let space = CGColorSpaceCreateDeviceRGB()
func color(_ r: CGFloat, _ g: CGFloat, _ b: CGFloat, _ a: CGFloat = 1) -> CGColor {
    CGColor(colorSpace: space, components: [r, g, b, a])!
}
func ellipse(_ ctx: CGContext, _ rect: CGRect, _ fill: CGColor) {
    ctx.setFillColor(fill); ctx.fillEllipse(in: rect)
}
func path(_ ctx: CGContext, _ points: [CGPoint], _ fill: CGColor) {
    ctx.beginPath(); ctx.move(to: points[0])
    for point in points.dropFirst() { ctx.addLine(to: point) }
    ctx.closePath(); ctx.setFillColor(fill); ctx.fillPath()
}
func draw(_ ctx: CGContext, _ time: Double) {
    let w = CGFloat(width), h = CGFloat(height)
    let gradient = CGGradient(colorsSpace: space, colors: [color(0.025,0.05,0.17), color(0.10,0.13,0.31)] as CFArray, locations: [0,1])!
    ctx.drawLinearGradient(gradient, start: CGPoint(x: 0,y: h), end: CGPoint(x: w,y: 0), options: [])
    // Stable seeded distribution, slow parallax, and a gentle brightness cycle.
    for i in 0..<100 {
        let x = (CGFloat((i * 173 + 61) % 1013) - CGFloat(time) * CGFloat(3 + i % 4)).truncatingRemainder(dividingBy: w)
        let y = CGFloat((i * 97 + 47) % height)
        let size = CGFloat(1 + i % 3)
        let alpha = CGFloat(0.36 + 0.3 * (1 + sin(time * 1.4 + Double(i))) / 2)
        ellipse(ctx, CGRect(x: x < 0 ? x+w : x,y: y,width: size,height: size), color(0.81,0.91,1,alpha))
    }
    // Friendly ringed planet.
    ctx.saveGState(); ctx.translateBy(x: 748 - CGFloat(time)*1.6, y: 385); ctx.rotate(by: -0.27)
    ctx.setStrokeColor(color(0.47,0.69,0.87,0.6)); ctx.setLineWidth(11)
    ctx.strokeEllipse(in: CGRect(x:-114,y:-28,width:228,height:56))
    ellipse(ctx, CGRect(x:-63,y:-63,width:126,height:126), color(0.38,0.62,0.78))
    ctx.saveGState(); ctx.addEllipse(in: CGRect(x:-63,y:-63,width:126,height:126)); ctx.clip()
    ctx.setFillColor(color(0.57,0.77,0.84)); ctx.fill(CGRect(x:-68,y:12,width:136,height:19))
    ctx.setFillColor(color(0.27,0.49,0.69)); ctx.fill(CGRect(x:-68,y:-30,width:136,height:12))
    ctx.restoreGState(); ctx.restoreGState()
    // A small, orbiting moon.
    ellipse(ctx, CGRect(x:155+CGFloat(sin(time*0.3))*12,y:367,width:48,height:48),color(0.82,0.82,0.70))
    ellipse(ctx, CGRect(x:165+CGFloat(sin(time*0.3))*12,y:379,width:12,height:12),color(0.66,0.68,0.61))
    // Rocket flies smoothly right, then loops back to its starting region.
    let phase = time / Double(seconds) * Double.pi * 2
    let rx = CGFloat(440 + sin(phase)*104)
    let ry = CGFloat(240 + sin(phase*2)*24)
    ctx.saveGState(); ctx.translateBy(x:rx,y:ry); ctx.rotate(by:CGFloat(-0.13 + cos(phase)*0.045))
    let flame = CGFloat(52 + sin(time*18)*7)
    path(ctx,[CGPoint(x:-68,y:-21),CGPoint(x:-68-flame,y:0),CGPoint(x:-68,y:21)],color(1,0.65,0.25))
    path(ctx,[CGPoint(x:-67,y:-10),CGPoint(x:-90-flame*0.4,y:0),CGPoint(x:-67,y:10)],color(1,0.91,0.50))
    path(ctx,[CGPoint(x:-54,y:19),CGPoint(x:-77,y:57),CGPoint(x:5,y:29)],color(0.99,0.45,0.36))
    path(ctx,[CGPoint(x:-54,y:-19),CGPoint(x:-77,y:-57),CGPoint(x:5,y:-29)],color(0.99,0.45,0.36))
    ellipse(ctx,CGRect(x:-75,y:-35,width:155,height:70),color(0.94,0.96,0.94))
    path(ctx,[CGPoint(x:47,y:31),CGPoint(x:98,y:0),CGPoint(x:47,y:-31)],color(0.99,0.45,0.36))
    ctx.setFillColor(color(0.29,0.57,0.68)); ctx.fill(CGRect(x:-72,y:-22,width:14,height:44))
    ellipse(ctx,CGRect(x:-15,y:-24,width:48,height:48),color(0.27,0.44,0.56))
    ellipse(ctx,CGRect(x:-9,y:-18,width:36,height:36),color(0.52,0.84,0.91))
    ellipse(ctx,CGRect(x:-2,y:4,width:10,height:8),color(0.88,0.98,1,0.9))
    ctx.restoreGState()
    // Quiet ground silhouette completes the scene; no embedded text or UI.
    ellipse(ctx,CGRect(x:-200,y:-690,width:1420,height:810),color(0.12,0.21,0.30))
    ellipse(ctx,CGRect(x:500,y:32,width:95,height:17),color(0.10,0.17,0.25))
    ellipse(ctx,CGRect(x:167,y:8,width:133,height:22),color(0.10,0.17,0.25))
}
for frame in 0..<(fps * seconds) {
    while !input.isReadyForMoreMediaData { Thread.sleep(forTimeInterval: 0.002) }
    autoreleasepool {
        var pixelBuffer: CVPixelBuffer?
        let status = CVPixelBufferPoolCreatePixelBuffer(kCFAllocatorDefault, adaptor.pixelBufferPool!, &pixelBuffer)
        guard status == kCVReturnSuccess, let buffer = pixelBuffer else { fatalError("Pixel buffer allocation failed") }
        CVPixelBufferLockBaseAddress(buffer, [])
        let ctx = CGContext(data:CVPixelBufferGetBaseAddress(buffer),width:width,height:height,bitsPerComponent:8,bytesPerRow:CVPixelBufferGetBytesPerRow(buffer),space:space,bitmapInfo:CGImageAlphaInfo.noneSkipFirst.rawValue)!
        draw(ctx, Double(frame)/Double(fps))
        CVPixelBufferUnlockBaseAddress(buffer, [])
        guard adaptor.append(buffer,withPresentationTime:CMTime(value:Int64(frame),timescale:Int32(fps))) else { fatalError("Append failed: \(String(describing: writer.error))") }
    }
}
input.markAsFinished()
let done = DispatchSemaphore(value:0)
writer.finishWriting { done.signal() }
done.wait()
guard writer.status == .completed else { fatalError("Encoding failed: \(String(describing: writer.error))") }
print("Created \(outputURL.path): \(width)x\(height), \(seconds)s, H.264, \(fps)fps, original silent demo")
