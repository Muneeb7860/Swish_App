import os
import re

domain_root = 'backend/src/main/java/ch/swissqcommerce/backend/domain'

imports_to_check = [
    ("BigDecimal", "import java.math.BigDecimal;"),
    ("OffsetDateTime", "import java.time.OffsetDateTime;"),
    ("List", "import java.util.List;"),
    ("ArrayList", "import java.util.ArrayList;"),
    ("UUID", "import java.util.UUID;"),
    ("Customer", "import ch.swissqcommerce.backend.model.Customer;"),
    ("DarkStore", "import ch.swissqcommerce.backend.model.DarkStore;"),
    ("Inventory", "import ch.swissqcommerce.backend.model.Inventory;"),
    ("Rider", "import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;"),
    ("Wholesaler", "import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;")
]

def add_imports_to_file(file_path):
    with open(file_path, 'r') as f:
        content = f.read()
        
    # Skip directories or files that don't need imports
    package_match = re.search(r'package\s+([^;]+);', content)
    if not package_match:
        return
        
    package_name = package_match.group(1)
    
    added_imports = []
    
    for word, imp_statement in imports_to_check:
        # Check if the class/word is present as a standalone token (e.g. not part of a larger class name like RiderRepository)
        # We can use regex word boundaries.
        if re.search(r'\b' + word + r'\b', content):
            # Check if this import is already present
            if imp_statement not in content:
                # Do not import a class from its own package
                own_pkg_class = imp_statement.replace("import ", "").replace(";", "")
                if own_pkg_class.rsplit(".", 1)[0] == package_name:
                    continue
                # Also ignore special class names in their packages
                if word == "Rider" and "domain.enrollment.core.model" in package_name:
                    continue
                if word == "Wholesaler" and "domain.wholesaler.core.model" in package_name:
                    continue
                added_imports.append(imp_statement)
                
    if added_imports:
        # Insert imports right after the package declaration
        pkg_decl = f"package {package_name};"
        new_imports = "\n" + "\n".join(added_imports) + "\n"
        content = content.replace(pkg_decl, pkg_decl + new_imports, 1)
        
        with open(file_path, 'w') as f:
            f.write(content)
        print(f"Added imports to {file_path}: {added_imports}")

for root, dirs, files in os.walk(domain_root):
    for file in files:
        if file.endswith('.java'):
            add_imports_to_file(os.path.join(root, file))

print("Completed adding missing imports.")
