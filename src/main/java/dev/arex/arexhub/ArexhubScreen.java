package dev.arex.arexhub;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Arexhub ana ekranı: özel çizilmiş panel, arama, kart listesi ve indirme butonları. */
public class ArexhubScreen extends Screen {
    // Renkler (ARGB)
    private static final int DIM = 0xC8000000;
    private static final int PANEL = 0xFF14141F;
    private static final int HEADER = 0xFF1C1C2B;
    private static final int ACCENT = 0xFF7C5CFF;
    private static final int ACCENT_HOVER = 0xFF9277FF;
    private static final int CARD = 0xFF222236;
    private static final int CARD_HOVER = 0xFF2D2D48;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9A9AB5;
    private static final int BTN_BUSY = 0xFF3A3A55;
    private static final int BTN_DONE = 0xFF2E8B57;
    private static final int BTN_FAIL = 0xFFB04040;

    private static final int HEADER_H = 40;
    private static final int CARD_H = 48;
    private static final int GAP = 6;
    private static final int BTN_W = 78;
    private static final int BTN_H = 20;

    // Oturum boyunca indirme durumları (paket id -> görev)
    private static final Map<String, DownloadTask> TASKS = new ConcurrentHashMap<>();

    private final Screen parent;
    private TextFieldWidget searchField;

    private volatile List<ArexPack> packs = List.of();
    private volatile String status = "";
    private volatile float scroll = 0f;
    private final AtomicInteger requestId = new AtomicInteger();

    private String lastQuery = "";
    private String pendingQuery = "";
    private int debounce = 0;

    private int panelX, panelY, panelW, panelH, listTop, listBottom;

    public ArexhubScreen(Screen parent) {
        super(Text.literal("Arexhub"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 30, 600);
        panelH = height - 30;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listTop = panelY + HEADER_H + 38;
        listBottom = panelY + panelH - 36;

        searchField = new TextFieldWidget(textRenderer, panelX + 12, panelY + HEADER_H + 9,
                panelW - 24, 20, Text.literal("Ara"));
        searchField.setPlaceholder(Text.literal("Texture pack ara..."));
        searchField.setMaxLength(60);
        searchField.setText(lastQuery);
        searchField.setChangedListener(s -> {
            pendingQuery = s;
            debounce = 12; // ~0.6 sn bekle
        });
        addDrawableChild(searchField);
        setInitialFocus(searchField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Kapat"), b -> close())
                .dimensions(panelX + panelW - 72, panelY + panelH - 28, 60, 20)
                .build());

        if (packs.isEmpty()) runSearch(lastQuery);
    }

    private void runSearch(String query) {
        lastQuery = query;
        int id = requestId.incrementAndGet();
        status = "Aranıyor...";
        ModrinthApi.search(query.trim()).thenAccept(list -> {
            if (id != requestId.get()) return;
            packs = list;
            scroll = 0f;
            status = list.isEmpty() ? "Sonuç bulunamadı" : "";
        }).exceptionally(ex -> {
            if (id == requestId.get()) {
                packs = List.of();
                status = "Bağlantı hatası, internetini kontrol et";
            }
            return null;
        });
    }

    @Override
    public void tick() {
        if (debounce > 0 && --debounce == 0) {
            runSearch(pendingQuery);
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    // Vanilla blur/arka planı kullanmıyoruz, kendimiz çiziyoruz.
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, DIM);

        // Panel + çerçeve
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, ACCENT);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);

        // Başlık
        ctx.fill(panelX, panelY, panelX + panelW, panelY + HEADER_H, HEADER);
        ctx.fill(panelX, panelY + HEADER_H - 2, panelX + panelW, panelY + HEADER_H, ACCENT);
        drawTitle(ctx);
        String ver = "Minecraft " + ModrinthApi.MC_VERSION;
        ctx.drawText(textRenderer, ver, panelX + panelW - 14 - textRenderer.getWidth(ver),
                panelY + 16, MUTED, false);

        // Liste
        List<ArexPack> list = packs;
        int cx = panelX + 10;
        int cx2 = panelX + panelW - 14;

        ctx.enableScissor(panelX + 6, listTop, panelX + panelW - 6, listBottom);
        int y = listTop - (int) scroll;
        for (ArexPack p : list) {
            if (y + CARD_H >= listTop && y <= listBottom) {
                drawCard(ctx, p, cx, cx2, y, mouseX, mouseY);
            }
            y += CARD_H + GAP;
        }
        ctx.disableScissor();

        // Durum yazısı (boş liste / yükleniyor)
        if (list.isEmpty() && !status.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status),
                    panelX + panelW / 2, (listTop + listBottom) / 2 - 4, MUTED);
        }

        // Kaydırma çubuğu
        float max = maxScroll();
        if (max > 0) {
            int trackH = listBottom - listTop;
            int barH = Math.max(24, (int) (trackH * (trackH / (trackH + max))));
            int barY = listTop + (int) ((trackH - barH) * (scroll / max));
            ctx.fill(panelX + panelW - 10, listTop, panelX + panelW - 6, listBottom, 0xFF0E0E16);
            ctx.fill(panelX + panelW - 10, barY, panelX + panelW - 6, barY + barH, ACCENT);
        }

        // Alt bilgi
        if (!list.isEmpty()) {
            ctx.drawText(textRenderer,
                    list.size() + " pack • indirilenler Texture Packs menüsünde görünür",
                    panelX + 14, panelY + panelH - 22, MUTED, false);
        }

        super.render(ctx, mouseX, mouseY, delta); // arama kutusu + Kapat butonu
    }

    private void drawTitle(DrawContext ctx) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(panelX + 14, panelY + 11, 0);
        m.scale(1.6f, 1.6f, 1f);
        Text arex = Text.literal("AREX").formatted(Formatting.BOLD);
        ctx.drawText(textRenderer, arex, 0, 0, ACCENT, true);
        ctx.drawText(textRenderer, Text.literal("HUB").formatted(Formatting.BOLD),
                textRenderer.getWidth(arex), 0, TEXT, true);
        m.pop();
    }

    private void drawCard(DrawContext ctx, ArexPack p, int cx, int cx2, int y, int mx, int my) {
        boolean inList = my >= listTop && my < listBottom;
        boolean hover = inList && mx >= cx && mx < cx2 && my >= y && my < y + CARD_H;
        ctx.fill(cx, y, cx2, y + CARD_H, hover ? CARD_HOVER : CARD);
        ctx.fill(cx, y, cx + 3, y + CARD_H, ACCENT);

        // "İkon": başlığın ilk harfi, başlığa göre renklendirilir
        int iconColor = 0xFF000000 | MathHelper.hsvToRgb((p.title().hashCode() & 0xFF) / 255f, 0.55f, 0.75f);
        ctx.fill(cx + 10, y + 8, cx + 42, y + 40, iconColor);
        String initial = p.title().isEmpty() ? "?" : p.title().substring(0, 1).toUpperCase(Locale.ROOT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(initial), cx + 26, y + 20, TEXT);

        int btnX = cx2 - 8 - BTN_W;
        int textX = cx + 52;
        int maxW = btnX - textX - 8;

        ctx.drawText(textRenderer, textRenderer.trimToWidth(p.title(), maxW), textX, y + 8, TEXT, true);
        ctx.drawText(textRenderer,
                textRenderer.trimToWidth("by " + p.author() + " • " + formatCount(p.downloads()) + " indirme", maxW),
                textX, y + 20, MUTED, false);
        ctx.drawText(textRenderer, textRenderer.trimToWidth(p.description(), maxW),
                textX, y + 32, MUTED, false);

        drawDownloadButton(ctx, p, btnX, y + (CARD_H - BTN_H) / 2, mx, my, inList);
    }

    private void drawDownloadButton(DrawContext ctx, ArexPack p, int bx, int by, int mx, int my, boolean inList) {
        DownloadTask task = TASKS.get(p.id());
        boolean hover = inList && mx >= bx && mx < bx + BTN_W && my >= by && my < by + BTN_H;
        String label;
        int color;

        if (task == null) {
            label = "İndir";
            color = hover ? ACCENT_HOVER : ACCENT;
            ctx.fill(bx, by, bx + BTN_W, by + BTN_H, color);
        } else if (task.status == DownloadTask.Status.DOWNLOADING) {
            float pr = task.progress;
            label = pr >= 0 ? (int) (pr * 100) + "%" : "...";
            ctx.fill(bx, by, bx + BTN_W, by + BTN_H, BTN_BUSY);
            if (pr > 0) ctx.fill(bx, by, bx + (int) (BTN_W * pr), by + BTN_H, ACCENT);
        } else if (task.status == DownloadTask.Status.DONE) {
            label = "Kuruldu";
            ctx.fill(bx, by, bx + BTN_W, by + BTN_H, BTN_DONE);
        } else {
            label = "Tekrar dene";
            ctx.fill(bx, by, bx + BTN_W, by + BTN_H, hover ? 0xFFD05050 : BTN_FAIL);
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), bx + BTN_W / 2, by + 6, TEXT);
    }

    private static String formatCount(int n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.ROOT, "%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private float maxScroll() {
        int total = packs.size() * (CARD_H + GAP) - GAP;
        return Math.max(0, total - (listBottom - listTop));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = MathHelper.clamp(scroll - (float) verticalAmount * 24f, 0f, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || mouseY < listTop || mouseY >= listBottom) return false;

        List<ArexPack> list = packs;
        int cx2 = panelX + panelW - 14;
        int bx = cx2 - 8 - BTN_W;

        int rel = (int) (mouseY - listTop + scroll);
        int index = rel / (CARD_H + GAP);
        int inCard = rel % (CARD_H + GAP);
        if (index < 0 || index >= list.size() || inCard >= CARD_H) return false;

        int by = (CARD_H - BTN_H) / 2;
        if (mouseX >= bx && mouseX < bx + BTN_W && inCard >= by && inCard < by + BTN_H) {
            ArexPack p = list.get(index);
            DownloadTask existing = TASKS.get(p.id());
            if (existing == null || existing.status == DownloadTask.Status.FAILED) {
                DownloadTask task = new DownloadTask();
                TASKS.put(p.id(), task);
                ModrinthApi.download(p, client.getResourcePackDir(), task);
            }
            return true;
        }
        return false;
    }
}
