import composeApp

extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let result = KotlinByteArray(size: Int32(count))
        withUnsafeBytes { (buffer: UnsafeRawBufferPointer) in
            let bytes = buffer.bindMemory(to: Int8.self)
            for index in 0..<bytes.count {
                result.set(index: Int32(index), value: bytes[index])
            }
        }
        return result
    }
}
