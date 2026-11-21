#pragma once

#include <algorithm>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <toml++/toml.hpp>

namespace pinyon_shift::android_config {

// toml++ reports one-based lines and Unicode columns. Keep the original
// text and use its parsed spans so comments and nested values stay intact.
class Document {
 public:
  explicit Document(std::string_view text) : root(toml::parse(text)), text_(text) {
    lines_.push_back(text.starts_with("\xef\xbb\xbf") ? 3 : 0);
    for (size_t i = lines_.front(); i < text.size(); ++i) {
      if (text[i] == '\n') lines_.push_back(i + 1);
    }
  }

  std::pair<size_t, size_t> ValueBounds(const toml::node& value) const {
    if (!value.is_value()) throw std::invalid_argument("A settings key refers to a table or array");
    return {Offset(value.source().begin), Offset(value.source().end)};
  }

  std::string_view Literal(const toml::node& value) const {
    const auto [start, end] = ValueBounds(value);
    return text_.substr(start, end - start);
  }

  std::pair<size_t, size_t> StatementBounds(const toml::node& value) const {
    const size_t end = ValueBounds(value).second;
    const size_t line_begin = lines_.at(value.source().begin.line - 1);
    const size_t newline = text_.find('\n', end);
    return {line_begin, newline == std::string_view::npos ? text_.size() : newline + 1};
  }

  size_t HeaderEnd() const {
    for (size_t start : lines_) {
      const size_t end = text_.find('\n', start);
      const auto line = text_.substr(start, end == std::string_view::npos ? end : end - start);
      const auto first = line.find_first_not_of(" \t\r");
      if (first != std::string_view::npos && line[first] != '#') return start;
    }
    return text_.size();
  }

  const toml::table root;

 private:
  size_t Offset(toml::source_position position) const {
    if (position.line == lines_.size() + 1 && position.column == 1) return text_.size();
    size_t offset = lines_.at(position.line - 1);
    for (uint32_t column = 1; column < position.column && offset < text_.size(); ++column) {
      ++offset;
      while (offset < text_.size() && (static_cast<unsigned char>(text_[offset]) & 0xc0) == 0x80) {
        ++offset;
      }
    }
    return offset;
  }

  std::string_view text_;
  std::vector<size_t> lines_;
};

inline toml::table Read(std::string_view text) {
  const Document document(text);
  toml::table settings;
  for (const auto& [key, node] : document.root) {
    if (node.is_string()) settings.insert(key.str(), node.as_string()->get());
    else if (node.is_value()) settings.insert(key.str(), document.Literal(node));
  }
  return settings;
}

inline std::string Update(std::string text, std::string_view changes) {
  const Document current(text);
  const Document patch(changes);
  struct Edit {
    size_t start;
    size_t end;
    std::string_view value;
  };
  std::vector<Edit> edits;
  std::string missing;
  const std::string_view newline = text.find("\r\n") != std::string::npos ? "\r\n" : "\n";
  for (const auto& [key, node] : patch.root) {
    const auto literal = patch.Literal(node);
    if (const auto* value = current.root.get(key.str())) {
      const auto [start, end] = current.ValueBounds(*value);
      edits.push_back({start, end, literal});
    } else {
      const auto [start, end] = patch.StatementBounds(node);
      auto statement = changes.substr(start, end - start);
      statement = statement.substr(0, statement.find_last_not_of("\r\n") + 1);
      missing.append(statement);
      missing.append(newline);
    }
  }
  const size_t insertion = current.HeaderEnd();
  std::sort(edits.begin(), edits.end(), [](const Edit& a, const Edit& b) { return a.start > b.start; });
  for (const auto& edit : edits) text.replace(edit.start, edit.end - edit.start, edit.value);
  if (!missing.empty()) {
    // HeaderEnd can be EOF on a file that contains only comments.
    const bool only_bom = insertion == 3 && text.starts_with("\xef\xbb\xbf");
    if (insertion > 0 && text[insertion - 1] != '\n' && !only_bom) {
      missing.insert(0, newline);
    }
    text.insert(insertion, missing);
  }
  (void)toml::parse(text);
  return text;
}

inline std::string Restore(std::string text, std::string_view before, std::string_view key) {
  const Document previous(before);
  if (const auto* value = previous.root.get(key)) {
    return Update(std::move(text), std::string(key) + " = " + std::string(previous.Literal(*value)));
  }
  const Document current(text);
  if (const auto* value = current.root.get(key)) {
    const auto [start, end] = current.StatementBounds(*value);
    text.erase(start, end - start);
  }
  return text;
}

}  // namespace pinyon_shift::android_config
