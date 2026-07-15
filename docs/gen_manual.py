#!/usr/bin/env python3
"""Genera MANUAL.pdf desde MANUAL.md usando fpdf2."""

import re
import os
import sys

from fpdf import FPDF

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MD_PATH = os.path.join(SCRIPT_DIR, "MANUAL.md")
PDF_PATH = os.path.expanduser("~/Desktop/MANUAL.pdf")

FONT_PATH = "/Library/Fonts/Arial Unicode.ttf"
BOLD_PATH = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"

if not os.path.exists(MD_PATH):
    print(f"ERROR: No se encuentra {MD_PATH}", file=sys.stderr)
    sys.exit(1)

# ── Parse markdown ──────────────────────────────────────────────
with open(MD_PATH, encoding="utf-8") as f:
    raw = f.read()

sections = []
current_title = None
current_lines = []

def flush():
    if current_title:
        sections.append((current_title, list(current_lines)))

for line in raw.split("\n"):
    if line.startswith("## "):
        flush()
        current_title = line[3:].strip()
        current_lines = []
    elif current_title is not None:
        current_lines.append(line)

flush()

# ── Build PDF ───────────────────────────────────────────────────
pdf = FPDF()
pdf.set_auto_page_break(auto=True, margin=20)

font_ok = os.path.exists(FONT_PATH) and os.path.exists(BOLD_PATH)
if font_ok:
    pdf.add_font("Uni", "", FONT_PATH)
    pdf.add_font("Uni", "B", BOLD_PATH)
else:
    pdf.add_font("Uni", "", "/System/Library/Fonts/Supplemental/Arial.ttf")
    pdf.add_font("Uni", "B", "/System/Library/Fonts/Supplemental/Arial Bold.ttf")

# Title page
pdf.add_page()
pdf.set_font("Uni", "B", 26)
pdf.ln(60)
pdf.cell(0, 14, "Mercaderistas", align="C", new_x="LMARGIN", new_y="NEXT")
pdf.set_font("Uni", "", 14)
pdf.cell(0, 10, "Manual de Usuario", align="C", new_x="LMARGIN", new_y="NEXT")
pdf.ln(40)
pdf.set_font("Uni", "", 10)
pdf.cell(0, 6, "Version 11.19", align="C", new_x="LMARGIN", new_y="NEXT")

# ── Helper to render a line ─────────────────────────────────────
def render_line(text, indent=0, bold=False):
    x = 16 + indent * 6
    pdf.set_x(x)
    w = pdf.w - 32 - indent * 6
    if bold:
        pdf.set_font("Uni", "B", 11)
    else:
        pdf.set_font("Uni", "", 11)
    pdf.multi_cell(w, 6, text)

def render_table(rows):
    n = len(rows[0])
    col_w = [(pdf.w - 48) // n] * n
    start_x = 24
    pdf.set_x(start_x)
    for i, h in enumerate(rows[0]):
        pdf.set_font("Uni", "B", 10)
        pdf.cell(col_w[i], 7, h, border=1)
    pdf.ln()
    for row in rows[1:]:
        pdf.set_x(start_x)
        for i, cell in enumerate(row):
            pdf.set_font("Uni", "", 10)
            pdf.cell(col_w[i], 7, cell, border=1)
        pdf.ln()

# ── Render sections ─────────────────────────────────────────────
for title, lines in sections:
    pdf.add_page()
    pdf.set_font("Uni", "B", 16)
    pdf.cell(0, 12, title, new_x="LMARGIN", new_y="NEXT")
    pdf.ln(4)

    table_rows = []
    in_table = False
    in_code = False

    for line in lines:
        stripped = line.strip()

        # Skip frontmatter
        if stripped in ("---", ""):
            if in_code:
                pdf.set_font("Uni", "", 11)
                in_code = False
            continue

        # Table detection
        if "|" in stripped and stripped.startswith("|"):
            parts = [p.strip() for p in stripped.split("|") if p.strip()]
            if parts:
                # Check if separator row (|---|)
                if re.match(r"^[-:\s|]+$", stripped):
                    continue
                table_rows.append(parts)
                in_table = True
            continue
        elif in_table and table_rows:
            render_table(table_rows)
            table_rows = []
            in_table = False
            pdf.ln(2)

        # Code block (inline)
        if stripped.startswith("```"):
            in_code = not in_code
            continue

        if in_code:
            pdf.set_font("Uni", "", 10)
            pdf.set_x(28)
            pdf.multi_cell(pdf.w - 48, 5, stripped)
            continue

        # Bullet list
        if stripped.startswith("- "):
            render_line(stripped[2:], indent=1)
            continue

        # Numbered list
        if re.match(r"^\d+\.\s", stripped):
            render_line(stripped, indent=0)
            continue

        # Bold text (marked with **)
        if stripped.startswith("**") and stripped.endswith("**"):
            render_line(stripped.strip("*"), bold=True)
            continue

        # Table header row
        if re.match(r"^\|.*\|$", stripped):
            parts = [p.strip() for p in stripped.split("|") if p.strip()]
            if parts:
                table_rows.append(parts)
                in_table = True
            continue

        if stripped:
            render_line(stripped)

    # Flush any remaining table
    if in_table and table_rows:
        render_table(table_rows)

pdf.output(PDF_PATH)
print(f"PDF generado: {PDF_PATH}")
