import os
import re

domain_root = 'backend/src/main/java/ch/swissqcommerce/backend/domain'

for root, dirs, files in os.walk(domain_root):
    if 'core/model' in root:
        for file in files:
            if file.endswith('.java'):
                path = os.path.join(root, file)
                with open(path, 'r') as f:
                    content = f.read()

                # Don't touch enums or interfaces or records
                if 'enum ' in content or 'interface ' in content or 'record ' in content:
                    continue

                if 'class ' in content:
                    # Add imports if missing
                    if 'import lombok.Data;' not in content:
                        content = content.replace('package ', 'package ', 1)
                        content = re.sub(r'(package [^;]+;\n+)', r'\1import lombok.Data;\nimport lombok.Builder;\nimport lombok.NoArgsConstructor;\nimport lombok.AllArgsConstructor;\n', content)
                    
                    # Add annotations if missing
                    if '@Data' not in content:
                        content = re.sub(r'(public class )', r'@Data\n@Builder\n@NoArgsConstructor\n@AllArgsConstructor\n\1', content)
                    elif '@Builder' not in content:
                        content = re.sub(r'(@Data\s*\n)', r'\1@Builder\n@NoArgsConstructor\n@AllArgsConstructor\n', content)

                    with open(path, 'w') as f:
                        f.write(content)
                    print(f"Added Lombok to {path}")
