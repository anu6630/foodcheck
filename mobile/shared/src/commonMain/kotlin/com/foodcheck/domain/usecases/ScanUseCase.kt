package com.foodcheck.domain.usecases

import com.foodcheck.domain.entities.ScanOutcome
import com.foodcheck.domain.repositories.IProductRepository

/** Accepts 1-2 photos (front-of-pack, optionally an ingredients label) for the scan agent. */
class ScanUseCase(private val repository: IProductRepository) {
    suspend operator fun invoke(images: List<ByteArray>): Result<ScanOutcome> {
        if (images.isEmpty()) return Result.failure(IllegalArgumentException("At least one image is required"))
        if (images.size > 2) return Result.failure(IllegalArgumentException("At most two images are supported"))
        return repository.scan(images)
    }
}
