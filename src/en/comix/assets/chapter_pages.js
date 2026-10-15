(pass) => (holder) => {
    const items = holder.items;
    if (holder.meta || !Array.isArray(items) || items.length === 0) return;
    if (!items.every((item) => item && typeof item.url === "string")) return;
    pass(JSON.stringify(holder));
}
