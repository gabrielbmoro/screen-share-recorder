# Screen Share Recorder

### Use case

- Record the screen

### Components

- `MainActivity` 🎨

The place where we will create the screen capture intent. This intent result is used to create the foreground service `ScreenCaptureForegroundService`.

- `ScreenCaptureForegroundService` 🧑‍🚀

This component will support two actions (triggered by `MainActivity`): *start recording*, and *stop recording*

The start recording initializes the `RecordHandler` sending an argument indicating the screen record shoud starts.

Also, `RecordHandler` has the stop recording action.

- `RecordHandler` 🎥

This handler will manage a common surface between `MediaCodec` and `VirtualDisplay`. While `VirtualDisplay` is filling the surface with data created by the screen share, the `MediaCodec` and `MediaMuxer` are creating a file with the video information. All this logic is under `RecordHandler` control.
