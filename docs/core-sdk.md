# Versioned SDK consumer deferred

Core and Spec are frozen and no matching versioned SDK release has been published. Android therefore continues to build the exact source identity in `core-bindings/core.lock.json`. The previously prepared SDK consumer/producer design is not activated or included as a runnable dependency in this change.

Do not enable SDK consumption or change the pin until an authorized matching SDK exists. No Core repository change, release creation, or secret change is part of this Android pull request.

The Android-only CI changes retain early release-key validation and final APK signer verification. Public Actions caches include dependency downloads only, not private Rust build intermediates.
