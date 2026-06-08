import os
import glob
import re

domain_root = 'backend/src/main/java/ch/swissqcommerce/backend/domain'

def clean_java_file(file_path):
    with open(file_path, 'r') as f:
        content = f.read()
    
    lines = content.splitlines()
    cleaned_lines = []
    seen_imports = set()
    seen_annotations = set()
    
    lombok_annotations = {"@Getter", "@Setter", "@Builder", "@Data", "@NoArgsConstructor", "@AllArgsConstructor", "@ToString", "@EqualsAndHashCode"}
    
    changed = False
    
    for line in lines:
        stripped = line.strip()
        
        # Check imports
        if stripped.startswith("import ") and stripped.endswith(";"):
            if stripped in seen_imports:
                changed = True
                continue  # skip duplicate import
            seen_imports.add(stripped)
            cleaned_lines.append(line)
            continue
            
        # Reset annotations seen set when we hit a class/interface/enum
        if "class " in line or "interface " in line or "enum " in line or "record " in line:
            seen_annotations.clear()
            cleaned_lines.append(line)
            continue
            
        # Check class/method annotations
        if stripped in lombok_annotations or stripped.startswith("@Builder") or stripped.startswith("@Data") or stripped.startswith("@NoArgsConstructor") or stripped.startswith("@AllArgsConstructor") or stripped.startswith("@Getter") or stripped.startswith("@Setter"):
            # Normalize to compare (e.g. ignore whitespace inside)
            norm = re.sub(r'\s+', '', stripped)
            if norm in seen_annotations:
                changed = True
                continue  # skip duplicate annotation
            seen_annotations.add(norm)
            cleaned_lines.append(line)
            continue
            
        cleaned_lines.append(line)
        
    if changed:
        with open(file_path, 'w') as f:
            f.write("\n".join(cleaned_lines) + "\n")
        print(f"Cleaned duplicates in {file_path}")

for root, dirs, files in os.walk(domain_root):
    for file in files:
        if file.endswith('.java'):
            clean_java_file(os.path.join(root, file))

print("All Java files checked and cleaned.")
