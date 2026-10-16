import SwiftUI

@main
struct SpellingBoardApp: App {
    @StateObject private var model = BoardModel()

    var body: some Scene {
        WindowGroup {
            BoardScreen()
                .environmentObject(model)
        }
    }
}
