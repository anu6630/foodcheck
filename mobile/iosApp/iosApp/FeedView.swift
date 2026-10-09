import SwiftUI
import composeApp

struct FeedView: View {
    var onIngredientClick: (String) -> Void
    @StateObject private var model = ObservableFeedModel()
    @State private var draft = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(model.selected == nil ? "Community" : "Story")
                .font(.system(size: 18, weight: .heavy))
                .foregroundColor(.white)
                .padding(.horizontal, 20)
            if let error = model.error {
                Text(error).foregroundColor(.red).font(.caption).padding(.horizontal, 20)
            }
            if let story = model.selected {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        Button("All stories") { model.close() }
                            .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                        Text(story.caption ?? "Label scan")
                            .font(.headline)
                            .foregroundColor(.white)
                        Text(story.disclaimer ?? "Status is for the substances on this label, not a certificate for the brand.")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.5))
                        FlowLayout(spacing: 8) {
                            ForEach(story.ingredients, id: \.self) { ing in
                                Button(action: { if let id = ing.id { onIngredientClick(id) } }) {
                                    IngredientChipView(ing: ing)
                                }
                            }
                        }
                        Button("Like (\(story.likeCount))") { model.like() }
                            .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                        Text("Comments").font(.headline).foregroundColor(.white)
                        ForEach(story.comments, id: \.id) { comment in
                            Text(comment.body).foregroundColor(.white.opacity(0.85)).font(.subheadline)
                        }
                        TextField("Write a comment", text: $draft)
                            .padding(10)
                            .background(Color.white.opacity(0.06))
                            .cornerRadius(8)
                            .foregroundColor(.white)
                        Button("Post comment") {
                            if !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                model.comment(draft)
                                draft = ""
                            }
                        }
                        .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                    }
                    .padding(.horizontal, 20)
                }
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(model.stories, id: \.id) { story in
                            Button(action: { model.open(id: story.id) }) {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text(story.caption?.isEmpty == false ? story.caption! : "Label scan")
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundColor(.white)
                                    Text(story.disclaimer ?? "Status is for the substances on this label, not a certificate for the brand.")
                                        .font(.caption)
                                        .foregroundColor(.white.opacity(0.5))
                                    Text("\(story.commentCount) comments · \(story.likeCount) likes")
                                        .font(.caption)
                                        .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(14)
                                .background(Color.white.opacity(0.04))
                                .cornerRadius(12)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
    }
}
