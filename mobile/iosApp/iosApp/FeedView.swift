import SwiftUI
import composeApp

struct FeedView: View {
    var onIngredientClick: (String) -> Void
    @StateObject private var model = ObservableFeedModel()
    @State private var draft = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let error = model.error {
                Text(error).foregroundColor(.red).font(.caption).padding(.horizontal, 16)
            }
            if let story = model.selected {
                storyDetail(story)
            } else {
                feedList()
            }
        }
    }

    @ViewBuilder
    private func feedList() -> some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                ForEach(Array(model.stories.enumerated()), id: \.element.id) { index, story in
                    StoryFeedCardView(
                        story: story,
                        liked: model.likedIds.contains(Int(story.id)),
                        onOpen: { model.open(id: story.id) },
                        onLike: { model.like(storyId: story.id) },
                        onComment: { model.open(id: story.id) }
                    )
                    .onAppear {
                        if index >= model.stories.count - 2 {
                            model.loadMore()
                        }
                    }
                }
                if model.loadingMore {
                    ProgressView().tint(Color(red: 0.06, green: 0.73, blue: 0.51))
                        .padding()
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 96)
        }
    }

    @ViewBuilder
    private func storyDetail(_ story: StoryDto) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Button(action: { model.close() }) {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("Community")
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                }
                Text(story.caption ?? "Label scan")
                    .font(.headline)
                    .foregroundColor(.white)
                Text(story.disclaimer ?? "Status is for the substances on this label, not a certificate for the brand.")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.5))
                FlowLayout(spacing: 8) {
                    ForEach(Array(story.ingredients.enumerated()), id: \.offset) { _, ing in
                        if ing.isGroup {
                            GroupIngredientChipView(ing: ing, expanded: false)
                        } else {
                            Button(action: { if let id = ing.id { onIngredientClick(id) } }) {
                                IngredientChipView(ing: ing)
                            }
                        }
                    }
                }
                StoryActionBarView(
                    liked: model.likedIds.contains(Int(story.id)),
                    likeCount: Int(story.likeCount),
                    commentCount: Int(story.commentCount),
                    onLike: { model.like(storyId: story.id) },
                    onComment: {},
                    onShare: {}
                )
                Text("Comments").font(.headline).foregroundColor(.white)
                ForEach(story.comments, id: \.id) { comment in
                    Text(comment.body).foregroundColor(.white.opacity(0.85)).font(.subheadline)
                }
                HStack(spacing: 8) {
                    TextField("Add a comment…", text: $draft)
                        .padding(10)
                        .background(Color.white.opacity(0.06))
                        .cornerRadius(8)
                        .foregroundColor(.white)
                    Button(action: {
                        if !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            model.comment(draft)
                            draft = ""
                        }
                    }) {
                        Image(systemName: "paperplane.fill")
                            .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }
}

struct StoryFeedCardView: View {
    let story: StoryDto
    let liked: Bool
    let onOpen: () -> Void
    let onLike: () -> Void
    let onComment: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(story.caption?.isEmpty == false ? story.caption! : "Label scan")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)
                .lineLimit(2)
            Text(story.disclaimer ?? "Status is for the substances on this label, not a certificate for the brand.")
                .font(.caption)
                .foregroundColor(.white.opacity(0.5))
                .lineLimit(2)
            CompactIngredientChipsView(ingredients: story.ingredients, maxVisible: 6)
            StoryActionBarView(
                liked: liked,
                likeCount: Int(story.likeCount),
                commentCount: Int(story.commentCount),
                onLike: onLike,
                onComment: onComment,
                onShare: {}
            )
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .background(Color.white.opacity(0.04))
        .cornerRadius(14)
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpen)
    }
}

struct CompactIngredientChipsView: View {
    let ingredients: [AnnotatedIngredient]
    let maxVisible: Int
    var onOverflowClick: (() -> Void)? = nil
    var onIngredientClick: ((AnnotatedIngredient) -> Void)? = nil

    var body: some View {
        let visible = Array(ingredients.prefix(maxVisible))
        let overflow = max(0, ingredients.count - visible.count)
        FlowLayout(spacing: 6) {
            ForEach(Array(visible.enumerated()), id: \.offset) { _, ing in
                CompactIngredientChipView(ing: ing)
                    .onTapGesture {
                        onIngredientClick?(ing)
                    }
            }
            if overflow > 0 {
                Text("+\(overflow) more")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.white.opacity(0.5))
                    .padding(.horizontal, 4)
                    .padding(.vertical, 4)
                    .onTapGesture {
                        onOverflowClick?()
                    }
            }
        }
    }
}

struct CompactIngredientChipView: View {
    let ing: AnnotatedIngredient

    var color: Color {
        switch ing.severity.lowercased() {
        case "red": return Color(red: 0.96, green: 0.25, blue: 0.37)
        case "amber": return Color(red: 0.96, green: 0.62, blue: 0.04)
        case "green": return Color(red: 0.20, green: 0.83, blue: 0.60)
        default: return .white.opacity(0.6)
        }
    }

    var label: String {
        if ing.isGroup {
            let role = (ing.role?.isEmpty == false ? ing.role! : ing.name)
            return "\(role) (\(ing.members.count))"
        }
        return ing.name.count > 22 ? String(ing.name.prefix(20)) + "…" : ing.name
    }

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: {
                switch ing.severity.lowercased() {
                case "green": return "checkmark.circle.fill"
                case "red": return "xmark.circle.fill"
                case "amber": return "exclamationmark.triangle.fill"
                default: return "questionmark.circle.fill"
                }
            }())
            .font(.system(size: 11))
            .foregroundColor(color)
            Text(label)
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(.white)
            if ing.showsWatch {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(Color(red: 0.96, green: 0.62, blue: 0.04))
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(color.opacity(0.10))
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(color.opacity(0.45), lineWidth: 1)
        )
    }
}

struct StoryActionBarView: View {
    let liked: Bool
    let likeCount: Int
    let commentCount: Int
    let onLike: () -> Void
    let onComment: () -> Void
    let onShare: () -> Void

    var body: some View {
        HStack(spacing: 16) {
            Button(action: onLike) {
                HStack(spacing: 4) {
                    Image(systemName: liked ? "heart.fill" : "heart")
                        .foregroundColor(liked ? Color(red: 0.96, green: 0.25, blue: 0.37) : .white)
                    Text("\(likeCount)")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white.opacity(0.85))
                }
            }
            Button(action: onComment) {
                HStack(spacing: 4) {
                    Image(systemName: "bubble.right")
                        .foregroundColor(.white)
                    Text("\(commentCount)")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white.opacity(0.85))
                }
            }
            Button(action: onShare) {
                Image(systemName: "square.and.arrow.up")
                    .foregroundColor(.white)
            }
            Spacer()
        }
        .font(.system(size: 18))
    }
}
