import os
import glob
import re

base_path = "backend/src/main/java/ch/swissqcommerce/backend"

# 1. Clean duplicate Lombok annotations in all Entity files
entity_files = glob.glob(f"{base_path}/domain/*/adapter/out/persistence/*Entity.java")
for f_path in entity_files:
    with open(f_path, "r") as f:
        content = f.read()
    
    # Let's clean the class-level annotations
    lines = content.splitlines()
    cleaned_lines = []
    seen = set()
    for line in lines:
        stripped_line = line.strip()
        if any(ann in stripped_line for ann in ["@Getter", "@Setter", "@NoArgsConstructor", "@AllArgsConstructor", "@Builder", "@Data"]):
            if stripped_line in seen:
                continue
            seen.add(stripped_line)
        cleaned_lines.append(line)
        
    with open(f_path, "w") as f:
        f.write("\n".join(cleaned_lines))
    print(f"Cleaned Lombok in {os.path.basename(f_path)}")

# 2. Add OrderItemId import in OrderItemEntity.java
path_oi_entity = f"{base_path}/domain/transaction/adapter/out/persistence/OrderItemEntity.java"
if os.path.exists(path_oi_entity):
    with open(path_oi_entity, "r") as f:
        content = f.read()
    if "import ch.swissqcommerce.backend.domain.transaction.core.model.OrderItemId;" not in content:
        content = content.replace("package ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence;", 
                                  "package ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence;\n\nimport ch.swissqcommerce.backend.domain.transaction.core.model.OrderItemId;")
        with open(path_oi_entity, "w") as f:
            f.write(content)
        print("Added OrderItemId import to OrderItemEntity.java")

# 3. Add java.math.BigDecimal import to Wholesaler.java
path_wholesaler = f"{base_path}/domain/wholesaler/core/model/Wholesaler.java"
if os.path.exists(path_wholesaler):
    with open(path_wholesaler, "r") as f:
        content = f.read()
    if "import java.math.BigDecimal;" not in content:
        content = content.replace("package ch.swissqcommerce.backend.domain.wholesaler.core.model;", 
                                  "package ch.swissqcommerce.backend.domain.wholesaler.core.model;\n\nimport java.math.BigDecimal;")
        with open(path_wholesaler, "w") as f:
            f.write(content)
        print("Added BigDecimal import to Wholesaler.java")

# 4. Clean builder() methods inside the clean Models that don't have builder annotations
# In PricingServiceImpl.java: CalculationResult doesn't compile with builder() because we stripped Lombok from CalculationResult!
# Yes, CalculationResult was rewritten to not use Lombok but wait, we forgot to add standard Builder to CalculationResult!
# Let's inspect CalculationResult.java first and fix it.
