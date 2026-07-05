import SwiftUI
import composeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(pickImage: { source, completion in
            guard let presenter = topMostViewController() else {
                _ = completion(nil)
                return
            }
            ImagePickerPresenter.present(source: source, from: presenter) { data in
                if let data = data {
                    _ = completion(data.toKotlinByteArray())
                } else {
                    _ = completion(nil)
                }
            }
        })
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        LandingView()
    }
}
