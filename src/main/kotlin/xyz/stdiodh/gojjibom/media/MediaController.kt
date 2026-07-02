package xyz.stdiodh.gojjibom.media

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.stdiodh.gojjibom.shared.ApiResponse

@RestController
@RequestMapping("/api/v1/media")
class MediaController(
    private val imageService: ImageService,
) {
    @PostMapping("/images/upload-url")
    fun createImageUploadUrl(
        @Valid @RequestBody request: ImageUploadUrlRequest,
    ): ResponseEntity<ApiResponse<ImageUploadUrlResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(imageService.createUploadUrl(request)))

    @PostMapping("/images")
    fun registerImage(
        @Valid @RequestBody request: RegisterImageRequest,
    ): ResponseEntity<ApiResponse<ImageResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(imageService.registerImage(request)))

    @GetMapping("/images/{id}/view-url")
    fun createImageViewUrl(
        @PathVariable id: Long,
        @RequestParam actorUserId: Long,
        @RequestParam careGroupId: Long,
    ): ApiResponse<ImageViewUrlResponse> =
        ApiResponse.success(
            imageService.createViewUrl(
                imageId = id,
                actorUserId = actorUserId,
                careGroupId = careGroupId,
            ),
        )
}
