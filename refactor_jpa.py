import os
import re

domain_root = 'backend/src/main/java/ch/swissqcommerce/backend/domain'

# Regexes
imports_re = re.compile(r'import\s+jakarta\.(persistence|validation)\.[^;]+;\n*')
class_annotations_re = re.compile(r'@(?:Entity|Table|NoArgsConstructor|AllArgsConstructor)\b[^\n]*\n*')
field_annotations_re = re.compile(r'\s*@(Id|Column|NotNull|NotBlank|Size|DecimalMin|DecimalMax|Enumerated|JoinColumn|OneToOne|OneToMany|ManyToOne|ManyToMany)\b[^\n]*\n*')

for root, dirs, files in os.walk(domain_root):
    if 'core/model' in root:
        for file in files:
            if file.endswith('.java'):
                path = os.path.join(root, file)
                with open(path, 'r') as f:
                    content = f.read()

                if '@Entity' in content:
                    class_name = file.replace('.java', '')
                    entity_class_name = f"{class_name}Entity"
                    
                    # Target path for Entity class
                    domain_name = root.split('/domain/')[1].split('/')[0]
                    adapter_dir = os.path.join(domain_root, domain_name, 'adapter/out/persistence')
                    os.makedirs(adapter_dir, exist_ok=True)
                    entity_path = os.path.join(adapter_dir, f"{entity_class_name}.java")

                    # Write the new JPA Entity class
                    entity_content = content.replace('package ch.swissqcommerce.backend.domain.' + domain_name + '.core.model;',
                                                     'package ch.swissqcommerce.backend.domain.' + domain_name + '.adapter.out.persistence;')
                    entity_content = entity_content.replace(f'public class {class_name}', f'public class {entity_class_name}')
                    with open(entity_path, 'w') as f:
                        f.write(entity_content)
                    print(f"Created {entity_path}")

                    # Clean the original core model
                    clean_content = content
                    clean_content = imports_re.sub('', clean_content)
                    clean_content = class_annotations_re.sub('', clean_content)
                    clean_content = field_annotations_re.sub('', clean_content)
                    
                    # Ensure Lombok @Builder and @Getter are still there (or add if missing)
                    if '@Getter' not in clean_content:
                        clean_content = clean_content.replace(f'public class {class_name}', f'@Getter\n@lombok.Builder\npublic class {class_name}')

                    with open(path, 'w') as f:
                        f.write(clean_content)
                    print(f"Cleaned {path}")
