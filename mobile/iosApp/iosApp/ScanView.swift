import SwiftUI
import composeApp

struct ScanView: View {
    @StateObject private var model = ObservableScanModel()
    @Environment(\.dismiss) var dismiss
    
    // Callbacks to notify parent views when a match is found
    var onConfidentMatch: (String) -> Void
    var onIngredientClick: (String) -> Void
    
    var body: some View {
        VStack(spacing: 24) {
            if model.loading {
                    Spacer()
                    VStack(spacing: 16) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: Color(red: 0.06, green: 0.73, blue: 0.51)))
                            .scaleEffect(1.5)
                        
                        Text(model.statusText)
                            .font(.headline)
                            .foregroundColor(.white.opacity(0.8))
                    }
                    Spacer()
                } else if let error = model.error {
                    Spacer()
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 48))
                            .foregroundColor(.red)
                        Text(error)
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                        
                        Button("Try Again") {
                            model.clear()
                        }
                        .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                    }
                    Spacer()
                } else if let outcome = model.outcome {
                    Spacer()
                    
                    if let confident = outcome as? ScanOutcome.Confident {
                        Button(action: {
                            onConfidentMatch(confident.product.product.barcode)
                            dismiss()
                        }) {
                            VStack(spacing: 14) {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 52))
                                    .foregroundColor(.green)
                                Text("Match Found!")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(.white)
                                Text(confident.product.product.name)
                                    .font(.body)
                                    .foregroundColor(.white.opacity(0.8))
                                    .multilineTextAlignment(.center)
                                Text("Tap to view details")
                                    .font(.caption)
                                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                            }
                            .padding(24)
                            .frame(maxWidth: .infinity)
                            .background(Color.white.opacity(0.04))
                            .cornerRadius(20)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(Color.white.opacity(0.12), lineWidth: 1)
                            )
                        }
                        .buttonStyle(ScaleButtonStyle())
                    } else if let suggestions = outcome as? ScanOutcome.Suggestions {
                        VStack(alignment: .leading, spacing: 14) {
                            Text("Disambiguation Needed")
                                .font(.headline)
                                .foregroundColor(.white)
                            Text("Multiple matching candidates were found. Select the correct one:")
                                .font(.subheadline)
                                .foregroundColor(.white.opacity(0.6))
                            
                            ScrollView {
                                LazyVStack(spacing: 10) {
                                    ForEach(suggestions.candidates, id: \.self) { candidate in
                                        Button(action: {
                                            onConfidentMatch(candidate.product.barcode)
                                            dismiss()
                                        }) {
                                            HStack {
                                                VStack(alignment: .leading, spacing: 4) {
                                                    Text(candidate.product.name)
                                                        .font(.system(size: 14, weight: .bold))
                                                        .foregroundColor(.white)
                                                        .lineLimit(1)
                                                    Text("Brand: \(candidate.product.brand ?? "Unknown")")
                                                        .font(.system(size: 12))
                                                        .foregroundColor(.gray)
                                                }
                                                Spacer()
                                                Image(systemName: "chevron.right")
                                                    .foregroundColor(.white.opacity(0.3))
                                            }
                                            .padding(16)
                                            .background(Color.white.opacity(0.04))
                                            .cornerRadius(12)
                                        }
                                    }
                                }
                            }
                        }
                    } else if let ingredientsOnly = outcome as? ScanOutcome.IngredientsOnly {
                        VStack(alignment: .leading, spacing: 14) {
                            Text("No Product Match")
                                .font(.headline)
                                .foregroundColor(.white)
                            Text("We couldn't match a product, but we found these ingredients:")
                                .font(.subheadline)
                                .foregroundColor(.white.opacity(0.6))
                            
                            ScrollView {
                                LazyVGrid(columns: [GridItem(.adaptive(minimum: 140))], alignment: .leading, spacing: 8) {
                                    ForEach(ingredientsOnly.ingredients, id: \.self) { annotated in
                                        Button(action: {
                                            onIngredientClick(annotated.id!)
                                            dismiss()
                                        }) {
                                            IngredientChipView(ing: annotated)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer()
                } else {
                    Spacer()
                    
                    // Pulsing custom scanner target
                    ScannerTargetView()
                    
                    Spacer()
                    
                    // Glass trigger buttons
                    HStack(spacing: 16) {
                        Button(action: { triggerPicker(source: .camera) }) {
                            VStack(spacing: 12) {
                                Image(systemName: "camera.shutter.button.fill")
                                    .font(.system(size: 26))
                                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                                    .frame(width: 56, height: 56)
                                    .background(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.12))
                                    .clipShape(Circle())
                                    .overlay(Circle().stroke(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.3), lineWidth: 0.5))
                                
                                Text("Camera Scan")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                                
                                Text("Take a label photo")
                                    .font(.system(size: 11))
                                    .foregroundColor(.white.opacity(0.4))
                                    .multilineTextAlignment(.center)
                            }
                            .padding(.vertical, 24)
                            .frame(maxWidth: .infinity)
                            .background(.ultraThinMaterial.opacity(0.95))
                            .cornerRadius(20)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
                            )
                        }
                        .buttonStyle(ScaleButtonStyle())
                        
                        Button(action: { triggerPicker(source: .gallery) }) {
                            VStack(spacing: 12) {
                                Image(systemName: "photo.fill.on.rectangle.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(.white.opacity(0.8))
                                    .frame(width: 56, height: 56)
                                    .background(Color.white.opacity(0.06))
                                    .clipShape(Circle())
                                    .overlay(Circle().stroke(Color.white.opacity(0.15), lineWidth: 0.5))
                                
                                Text("Upload Image")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                                
                                Text("Select from library")
                                    .font(.system(size: 11))
                                    .foregroundColor(.white.opacity(0.4))
                                    .multilineTextAlignment(.center)
                            }
                            .padding(.vertical, 24)
                            .frame(maxWidth: .infinity)
                            .background(.ultraThinMaterial.opacity(0.95))
                            .cornerRadius(20)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
                            )
                        }
                        .buttonStyle(ScaleButtonStyle())
                    }
                    .padding(.horizontal, 20)
                    
                    Spacer()
                }
            }
        }
    
    private func triggerPicker(source: ImagePickerSource) {
        guard let controller = topMostViewController() else { return }
        ImagePickerPresenter.present(source: source, from: controller) { data in
            if let data = data {
                model.scan(image: data)
            }
        }
    }
}

struct ScannerTargetView: View {
    @State private var rotateDashes = 0.0
    @State private var scalePulse = 1.0
    
    var body: some View {
        VStack(spacing: 12) {
            ZStack {
                // Rotating outer dashed border
                Circle()
                    .stroke(style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round, dash: [8, 8]))
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.6))
                    .frame(width: 190, height: 190)
                    .rotationEffect(.degrees(rotateDashes))
                    
                // Corner brackets mimicking scanner framing guides
                ScannerBrackets()
                    .stroke(Color.white.opacity(0.25), lineWidth: 2)
                    .frame(width: 220, height: 220)
                
                // Pulsing inner lens
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.12), .clear],
                            center: .center,
                            startRadius: 0,
                            endRadius: 80
                        )
                    )
                    .frame(width: 160, height: 160)
                    .scaleEffect(scalePulse)
                
                Image(systemName: "camera.viewfinder")
                    .font(.system(size: 40))
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
            }
            .frame(height: 240)
            .onAppear {
                withAnimation(.linear(duration: 20).repeatForever(autoreverses: false)) {
                    rotateDashes = 360
                }
                withAnimation(.easeInOut(duration: 1.8).repeatForever(autoreverses: true)) {
                    scalePulse = 1.1
                }
            }
            
            Text("Align ingredients label inside the camera frame")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.white.opacity(0.4))
        }
    }
}

struct ScannerBrackets: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let length: CGFloat = 20
        
        // Top Left
        path.move(to: CGPoint(x: rect.minX, y: rect.minY + length))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.minX + length, y: rect.minY))
        
        // Top Right
        path.move(to: CGPoint(x: rect.maxX - length, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + length))
        
        // Bottom Left
        path.move(to: CGPoint(x: rect.minX, y: rect.maxY - length))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX + length, y: rect.maxY))
        
        // Bottom Right
        path.move(to: CGPoint(x: rect.maxX - length, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - length))
        
        return path
    }
}
