import type { ProductCategory } from "@/types/api";

export function resolveCategoryAndDescendantIds(
  categories: ProductCategory[],
  categoryId: number | null,
): Set<number> {
  if (categoryId == null) {
    return new Set<number>();
  }

  const resolved = new Set<number>([categoryId]);
  let frontier = [categoryId];
  while (frontier.length > 0) {
    const children = categories
      .filter((category) => category.parentId != null && frontier.includes(category.parentId))
      .map((category) => category.id)
      .filter((id) => !resolved.has(id));

    if (children.length === 0) {
      break;
    }
    for (const id of children) {
      resolved.add(id);
    }
    frontier = children;
  }

  return resolved;
}

export function formatCategoryOptionLabel(category: ProductCategory): string {
  const depth = Math.max(category.depth ?? 0, 0);
  const indent = "　".repeat(depth);
  return `${indent}${category.name} (${category.code})`;
}
