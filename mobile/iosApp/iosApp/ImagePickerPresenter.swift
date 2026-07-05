import UIKit
import PhotosUI
import composeApp

/// Presents the native gallery (PHPickerViewController) or camera (UIImagePickerController) and
/// hands the picked image back as JPEG `Data`. Kept entirely in Swift/UIKit rather than
/// hand-written Kotlin/Native cinterop delegate conformance - see MainViewController.kt for why.
final class ImagePickerPresenter: NSObject, PHPickerViewControllerDelegate, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    private var completion: ((Data?) -> Void)?
    // Keeps this delegate alive for the duration of the async picker flow.
    private static var active: ImagePickerPresenter?

    static func present(source: ImagePickerSource, from viewController: UIViewController, completion: @escaping (Data?) -> Void) {
        let presenter = ImagePickerPresenter()
        presenter.completion = completion
        active = presenter
        if source == ImagePickerSource.camera {
            presenter.presentCamera(from: viewController)
        } else {
            presenter.presentGallery(from: viewController)
        }
    }

    private func presentGallery(from viewController: UIViewController) {
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = self
        viewController.present(picker, animated: true)
    }

    private func presentCamera(from viewController: UIViewController) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            finish(nil)
            return
        }
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = self
        viewController.present(picker, animated: true)
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let provider = results.first?.itemProvider, provider.canLoadObject(ofClass: UIImage.self) else {
            finish(nil)
            return
        }
        provider.loadObject(ofClass: UIImage.self) { [weak self] image, _ in
            let data = (image as? UIImage)?.jpegData(compressionQuality: 0.9)
            DispatchQueue.main.async {
                self?.finish(data)
            }
        }
    }

    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)
        let image = info[.originalImage] as? UIImage
        finish(image?.jpegData(compressionQuality: 0.9))
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        finish(nil)
    }

    private func finish(_ data: Data?) {
        completion?(data)
        completion = nil
        ImagePickerPresenter.active = nil
    }
}

func topMostViewController() -> UIViewController? {
    guard let root = UIApplication.shared.connectedScenes
        .compactMap({ $0 as? UIWindowScene })
        .first?.windows.first(where: { $0.isKeyWindow })?.rootViewController else {
        return nil
    }
    var top = root
    while let presented = top.presentedViewController {
        top = presented
    }
    return top
}
