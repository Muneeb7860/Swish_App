import os
import re

directories = [
    'backend/src/main/java/ch/swissqcommerce/backend/domain/wholesaler/core/model',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/auth/core/model',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/reward/core/model',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/feedback/core/model',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/agent/port/out',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/enrollment/adapter/in/web',
    'backend/src/main/java/ch/swissqcommerce/backend/model'
]

def add_lombok(filepath):
    if not os.path.exists(filepath):
        return
        
    with open(filepath, 'r') as f:
        content = f.read()

    if 'enum ' in content or 'interface ' in content or 'record ' in content:
        return

    changed = False

    # ensure imports exist
    if 'import lombok.Data;' not in content and 'public class' in content:
        content = re.sub(r'(package [^;]+;)', r'\1\n\nimport lombok.Data;\nimport lombok.Builder;\nimport lombok.NoArgsConstructor;\nimport lombok.AllArgsConstructor;\n', content)
        changed = True

    # ensure annotations exist
    if '@Data' not in content and 'public class' in content:
        content = re.sub(r'(public class )', r'@Data\n@Builder\n@NoArgsConstructor\n@AllArgsConstructor\n\1', content)
        changed = True
        
    # Also fix static inner classes
    if '@Data' not in content and 'public static class' in content:
        content = re.sub(r'(public static class )', r'@Data\n@Builder\n@NoArgsConstructor\n@AllArgsConstructor\n\1', content)
        changed = True

    if changed:
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Fixed {filepath}")

for d in directories:
    if os.path.exists(d):
        for root, dirs, files in os.walk(d):
            for file in files:
                if file.endswith('.java'):
                    add_lombok(os.path.join(root, file))

print("Lombok annotations injected.")
