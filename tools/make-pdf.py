#!/usr/bin/env python3
"""
Генератор PDF-описания проекта TurboFetch.

Документ делается на reportlab: он не требует LaTeX и умеет кириллицу
через шрифты DejaVu (лежат вместе с matplotlib).

Запуск:  ~/ml/.venv/bin/python tools/make-pdf.py
"""

import os
import subprocess
from datetime import date

from reportlab.lib import colors
from reportlab.lib.enums import TA_JUSTIFY
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    KeepTogether,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "TurboFetch.pdf")

# --- Шрифты -------------------------------------------------------------

def _font_dir():
    import matplotlib
    return os.path.join(os.path.dirname(matplotlib.__file__), "mpl-data", "fonts", "ttf")


def register_fonts():
    d = _font_dir()
    pdfmetrics.registerFont(TTFont("Sans", os.path.join(d, "DejaVuSans.ttf")))
    pdfmetrics.registerFont(TTFont("Sans-Bold", os.path.join(d, "DejaVuSans-Bold.ttf")))
    pdfmetrics.registerFont(TTFont("Sans-Oblique", os.path.join(d, "DejaVuSans-Oblique.ttf")))
    pdfmetrics.registerFont(TTFont("Serif", os.path.join(d, "DejaVuSerif.ttf")))
    pdfmetrics.registerFont(TTFont("Serif-Bold", os.path.join(d, "DejaVuSerif-Bold.ttf")))
    pdfmetrics.registerFont(TTFont("Mono", os.path.join(d, "DejaVuSansMono.ttf")))
    pdfmetrics.registerFont(TTFont("Mono-Bold", os.path.join(d, "DejaVuSansMono-Bold.ttf")))
    pdfmetrics.registerFontFamily("Sans", normal="Sans", bold="Sans-Bold",
                                  italic="Sans-Oblique")


# --- Палитра (та же, что в приложении) ---------------------------------

INK = colors.HexColor("#16181C")
INK_SOFT = colors.HexColor("#6B6862")
INK_FAINT = colors.HexColor("#8A8578")
PAPER = colors.HexColor("#FAF9F6")
WASH = colors.HexColor("#F1EEE6")
HAIRLINE = colors.HexColor("#DDD8CC")
RUST = colors.HexColor("#9C4A2F")


# --- Стили --------------------------------------------------------------

def styles():
    base = getSampleStyleSheet()
    s = {}

    s["title"] = ParagraphStyle(
        "title", parent=base["Title"], fontName="Serif", fontSize=26, leading=31,
        textColor=INK, alignment=0, spaceAfter=4,
    )
    s["subtitle"] = ParagraphStyle(
        "subtitle", parent=base["Normal"], fontName="Sans", fontSize=10.5, leading=15,
        textColor=INK_FAINT, spaceAfter=16,
    )
    s["h1"] = ParagraphStyle(
        "h1", parent=base["Heading1"], fontName="Serif", fontSize=17, leading=22,
        textColor=INK, spaceBefore=16, spaceAfter=7,
    )
    s["h2"] = ParagraphStyle(
        "h2", parent=base["Heading2"], fontName="Sans-Bold", fontSize=11.5, leading=16,
        textColor=INK, spaceBefore=11, spaceAfter=4,
    )
    s["body"] = ParagraphStyle(
        "body", parent=base["BodyText"], fontName="Sans", fontSize=9.6, leading=15,
        textColor=INK, alignment=TA_JUSTIFY, spaceAfter=6,
    )
    s["bullet"] = ParagraphStyle(
        "bullet", parent=s["body"], leftIndent=12, bulletIndent=2, spaceAfter=3,
    )
    s["mono"] = ParagraphStyle(
        "mono", parent=base["Code"], fontName="Mono", fontSize=8.4, leading=12.5,
        textColor=INK, backColor=WASH, borderPadding=(4, 6, 4, 6),
        spaceBefore=3, spaceAfter=8,
    )
    s["caption"] = ParagraphStyle(
        "caption", parent=base["Normal"], fontName="Sans-Oblique", fontSize=8.6,
        leading=12, textColor=INK_FAINT, spaceAfter=10,
    )
    s["cell"] = ParagraphStyle(
        "cell", parent=base["Normal"], fontName="Sans", fontSize=8.8, leading=12.5,
        textColor=INK,
    )
    s["cellb"] = ParagraphStyle(
        "cellb", parent=s["cell"], fontName="Sans-Bold",
    )
    return s


# --- Содержимое ---------------------------------------------------------

def bullets(items, style):
    return [Paragraph(f"•&nbsp;&nbsp;{t}", style) for t in items]


def table(rows, widths, st, header=True):
    data = []
    for i, row in enumerate(rows):
        data.append([
            Paragraph(c, st["cellb"] if (header and i == 0) else st["cell"])
            for c in row
        ])
    t = Table(data, colWidths=widths, hAlign="LEFT")
    style_cmds = [
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("LINEBELOW", (0, 0), (-1, -2), 0.4, HAIRLINE),
    ]
    if header:
        style_cmds += [
            ("LINEBELOW", (0, 0), (-1, 0), 0.8, INK),
            ("BACKGROUND", (0, 0), (-1, 0), PAPER),
        ]
    t.setStyle(TableStyle(style_cmds))
    return t


def build(st):
    flow = []

    # Титул
    flow.append(Paragraph("TurboFetch", st["title"]))
    flow.append(Paragraph(
        "Нативное Android-приложение для скачивания видео напрямую с CDN "
        "без прокси и рекламы", st["subtitle"]))

    flow.append(Paragraph("1. Проблема", st["h1"]))
    flow.append(Paragraph(
        "Существующие приложения для скачивания видео работают по одной схеме: "
        "трафик идёт через серверы самого сервиса. Из этого следуют три "
        "недостатка, которые раздражают пользователя.", st["body"]))
    flow += bullets([
        "<b>Искусственное замедление.</b> Файл качается не с той скорости, "
        "которую отдаёт CDN: скорость режут, чтобы держать пользователя на сервисе.",
        "<b>Реклама.</b> Баннеры и межстраничные переходы в приложениях, "
        "которые по определению должны быть утилитой.",
        "<b>Зависимость от чужой инфраструктуры.</b> Если сервис лежит, "
        "не работает и приложение, хотя сам файл лежит на публичном CDN и "
        "доступен без всяких посредников.",
    ], st["bullet"])
    flow.append(Spacer(1, 6))

    flow.append(Paragraph("2. Идея решения", st["h1"]))
    flow.append(Paragraph(
        "TurboFetch не является агрегатором: он не хранит видео и не раздаёт "
        "его. Приложение умеет ровно три вещи — разобрать ссылку площадки, "
        "достать прямую ссылку на файл и скачать его напрямую с CDN, с той "
        "же скоростью, с какой отдаёт сам сервер.", st["body"]))
    flow.append(Paragraph(
        "Ключевое архитектурное требование: <b>постоянно работающего сервера "
        "нет и не требуется</b>. Всё, что нужно для разбора и скачивания, "
        "умещается в приложение и выполняется на устройстве. Это же делает "
        "возможным установку APK в обход магазина приложений.", st["body"]))

    flow.append(Paragraph("3. Требования", st["h1"]))
    flow.append(table([
        ["Требование", "Как выполнено"],
        ["Скачивание напрямую с CDN, без прокси",
         "HTTP-запрос идёт к CDN площадки; промежуточных серверов нет"],
        ["Отсутствие рекламы",
         "В приложении нет ни рекламных SDK, ни аналитики сторонних сервисов"],
        ["Работа без выделенного сервера",
         "Разбор ссылок и скачивание выполняются на устройстве"],
        ["Список задач с очередью",
         "Задания хранятся в Room, порядок задаётся перетаскиванием"],
        ["Настройка качества и имени файла",
         "Пресет качества и шаблон имени с живым предпросмотром"],
        ["Поддержка YouTube, TikTok, X",
         "Собственные резолверы для каждой площадки"],
        ["Скачивание аудио в mp3",
         "ffmpeg, собранный под arm64-v8a через NDK"],
        ["Устойчивость к обрыву связи",
         "Докачка через Range и повтор с экспоненциальной задержкой"],
    ], [58 * mm, 106 * mm], st))

    flow.append(Paragraph("4. Архитектура", st["h1"]))
    flow.append(Paragraph(
        "Приложение разделено на слои так, чтобы движок заменялся независимо "
        "от интерфейса. Единственный шов между ними — интерфейс "
        "<font face='Mono' size='8.4'>DownloadEngine</font>.", st["body"]))

    flow.append(table([
        ["Слой", "Ответственность", "Ключевые типы"],
        ["ui", "Экраны Compose и ViewModel'ы",
         "QueueScreen, ShrineScreen, FilesScreen"],
        ["domain", "Модель задачи и правила",
         "DownloadTask, MediaFormat, AppSettings"],
        ["data", "Room, хранилище, резолверы площадок",
         "TaskDao, FileDao, TikTokResolver"],
        ["worker", "Фоновая загрузка через WorkManager",
         "DownloadWorker, DownloadScheduler"],
    ], [22 * mm, 68 * mm, 74 * mm], st))

    flow.append(Spacer(1, 8))
    flow.append(Paragraph("4.1. Поток данных", st["h2"]))
    flow.append(Paragraph(
        "1. Ссылка попадает в очередь — из «Поделиться», из поля ввода или "
        "из клика в браузере.<br/>"
        "2. Резолвер площадки возвращает список форматов: для каждого — "
        "прямая ссылка, битрейт и необходимые заголовки.<br/>"
        "3. Пользователь выбирает качество; для DASH-видео подбирается "
        "отдельная аудиодорожка.<br/>"
        "4. WorkManager запускает загрузку в foreground-сервисе: видео и звук "
        "качаются отдельно и склеиваются через MediaMuxer.<br/>"
        "5. Готовый файл записывается в MediaStore, а задача переезжает во "
        "вкладку «Скачанное».", st["body"]))

    flow.append(Paragraph("5. Разбор ссылок", st["h1"]))
    flow.append(Paragraph(
        "Это самая хрупкая часть проекта: каждая площадка периодически "
        "меняет разметку, и это ломает парсеры. Таблица ниже — состояние на "
        "момент написания документа.", st["body"]))
    flow.append(table([
        ["Площадка", "Метод", "Особенности"],
        ["YouTube", "NewPipeExtractor (JitPack)",
         "До 2160p. Видео DASH и звук качаются отдельно"],
        ["TikTok", "Разбор JSON в HTML страницы",
         "Две схемы: api-data и __UNIVERSAL_DATA_FOR_REHYDRATION__"],
        ["X (Twitter)", "Эндпоинт cdn.syndication.twimg.com",
         "Токен в URL не валидируется; слой заменяемый"],
    ], [30 * mm, 55 * mm, 79 * mm], st))

    flow.append(Paragraph("6. Технические решения", st["h1"]))

    flow.append(Paragraph("6.1. Склейка видео и звука", st["h2"]))
    flow.append(Paragraph(
        "YouTube отдаёт видео и звук отдельными потоками. Склеиваются они "
        "штатными MediaExtractor и MediaMuxer — без нативного кода и без "
        "перекодирования: потоки копируются как есть.", st["body"]))

    flow.append(Paragraph("6.2. Конвертация в mp3", st["h2"]))
    flow.append(Paragraph(
        "ffmpeg собирается из исходников через NDK r27c под arm64-v8a с "
        "выравниванием страниц памяти 16 КБ (требование Android 15). "
        "Бинарник кладётся в <font face='Mono' size='8.4'>nativeLibraryDir</font> "
        "под именем <font face='Mono' size='8.4'>lib*.so</font> — только "
        "оттуда Android разрешает запускать исполняемые файлы (политика "
        "W^X). Сборка воспроизводима: <font face='Mono' size='8.4'>tools/build-ffmpeg.sh</font>.", st["body"]))

    flow.append(Paragraph("6.3. Устойчивость к обрывам", st["h2"]))
    flow.append(Paragraph(
        "Обрыв связи не считается ошибкой: воркер повторяет попытку с "
        "экспоненциальной задержкой (30 с), а частично скачанный файл "
        "докачивается запросом Range с нужного смещения. Ссылка TikTok или "
        "YouTube подписана и живёт недолго, поэтому перед повтором она "
        "разбирается заново.", st["body"]))

    flow.append(Paragraph("6.4. Хранение", st["h2"]))
    flow.append(Paragraph(
        "Очередь задач и история скачанных файлов — две отдельные таблицы "
        "Room. После скачивания задача уходит из очереди и остаётся в "
        "истории: очередь нужна только для того, что ещё предстоит "
        "скачать. Миграции базы написаны явно, поэтому обновление "
        "приложения не стирает данные.", st["body"]))

    flow.append(Paragraph("7. Сборка", st["h1"]))
    flow.append(Paragraph(
        "Требуется JDK 17+ и Android SDK (platform 37, build-tools 36). "
        "Сборка идёт через Gradle-обёртку:", st["body"]))
    flow.append(Paragraph(
        "./gradlew :app:assembleDebug<br/>"
        "adb install -r app/build/outputs/apk/debug/app-debug.apk",
        st["mono"]))
    flow.append(Paragraph(
        "Подробная инструкция, включая сборку ffmpeg, — в README.md.", st["caption"]))

    flow.append(Paragraph("8. Текущее состояние", st["h1"]))
    flow.append(table([
        ["Часть", "Состояние"],
        ["YouTube: разбор и скачивание до 2160p", "Работает, проверено на устройстве"],
        ["TikTok: разбор и скачивание", "Работает, проверено на устройстве"],
        ["X: разбор и скачивание", "Работает, проверено на устройстве"],
        ["mp3 через ffmpeg", "Работает, бинарник собран под arm64-v8a"],
        ["Склейка видео и звука (MediaMuxer)", "Работает"],
        ["Докачка после обрыва", "Работает"],
        ["Очередь, порядок, лимит параллельных загрузок", "Работает"],
        ["История скачанных файлов", "Работает"],
        ["Покрытие юнит-тестами", "32 теста, все зелёные"],
    ], [72 * mm, 92 * mm], st))

    flow.append(Paragraph("9. Ограничения и дальнейшая работа", st["h1"]))
    flow += bullets([
        "Слой разбора X недолговечен: эндпоинт не документирован и может "
        "измениться без предупреждения.",
        "TikTok отдаёт разметку в двух схемах и периодически их меняет — "
        "парсер придётся обновлять.",
        "mp3 доступен только на arm64-v8a: бинарник ffmpeg собран под одну "
        "архитектуру.",
        "Качество YouTube ограничено тем, что отдаёт площадка без "
        "аутентификационных токенов.",
        "Приложение не публикуется в магазинах, поэтому распространяется "
        "установкой APK в обход.",
    ], st["bullet"])

    flow.append(Spacer(1, 10))
    flow.append(Paragraph(
        "Документ подготовлен автоматически из исходного кода проекта. "
        "Актуальные версии: main (основная) и fun-branch (экспериментальная, "
        "со святыней Питера Тиля и темой «Warm Paper»).",
        st["caption"]))

    return flow


def git_rev():
    try:
        return subprocess.check_output(
            ["git", "-C", ROOT, "rev-parse", "--short", "HEAD"],
            text=True).strip()
    except Exception:
        return "?"


def make_page(canv, doc):
    canv.saveState()
    # Бумажный фон
    canv.setFillColor(PAPER)
    canv.rect(0, 0, A4[0], A4[1], fill=1, stroke=0)

    canv.setFont("Sans", 7.4)
    canv.setFillColor(INK_FAINT)
    canv.drawString(20 * mm, 12 * mm, "TurboFetch — описание проекта")
    canv.drawRightString(A4[0] - 20 * mm, 12 * mm, f"стр. {canv.getPageNumber()}")

    # Тонкая линия-подвал
    canv.setStrokeColor(HAIRLINE)
    canv.setLineWidth(0.4)
    canv.line(20 * mm, 16 * mm, A4[0] - 20 * mm, 16 * mm)
    canv.restoreState()


def main():
    register_fonts()
    st = styles()

    os.makedirs(os.path.dirname(OUT), exist_ok=True)

    doc = BaseDocTemplate(
        OUT,
        pagesize=A4,
        leftMargin=20 * mm,
        rightMargin=20 * mm,
        topMargin=18 * mm,
        bottomMargin=20 * mm,
        title="TurboFetch — описание проекта",
        author="TurboFetch",
        subject="Android-приложение для скачивания видео напрямую с CDN",
    )

    frame = Frame(
        doc.leftMargin, doc.bottomMargin,
        doc.width, doc.height, id="body",
    )
    doc.addPageTemplates([PageTemplate(id="all", frames=[frame], onPage=make_page)])

    doc.build(build(st))
    print(f"Готово: {OUT} ({os.path.getsize(OUT) // 1024} КБ, ревизия {git_rev()})")


if __name__ == "__main__":
    main()
