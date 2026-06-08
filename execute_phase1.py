import os
import re

base_path = "backend/src/main/java/ch/swissqcommerce/backend"

# 1. Strip JPA and Validation annotations from a Java class file and write the pure model
def strip_jpa_and_validation(content, package_name, class_name):
    # Strip imports
    content = re.sub(r'import jakarta\.persistence\..*?;', '', content)
    content = re.sub(r'import jakarta\.validation\..*?;', '', content)
    content = re.sub(r'import com\.fasterxml\.jackson\.annotation\..*?;', '', content)
    
    # Strip class-level annotations
    content = re.sub(r'@Entity', '', content)
    content = re.sub(r'@Table\(.*?\)', '', content)
    content = re.sub(r'@IdClass\(.*?\)', '', content)
    
    # Strip field-level annotations
    content = re.sub(r'@Id', '', content)
    content = re.sub(r'@GeneratedValue\(.*?\)', '', content)
    content = re.sub(r'@Column\(.*?\)', '', content)
    content = re.sub(r'@ManyToOne\(.*?\)', '', content)
    content = re.sub(r'@OneToMany\(.*?\)', '', content)
    content = re.sub(r'@ManyToMany\(.*?\)', '', content)
    content = re.sub(r'@JoinColumn\(.*?\)', '', content)
    content = re.sub(r'@Version', '', content)
    content = re.sub(r'@JsonIgnore', '', content)
    
    # Strip validation annotations
    content = re.sub(r'@NotNull', '', content)
    content = re.sub(r'@Size\(.*?\)', '', content)
    content = re.sub(r'@NotBlank', '', content)
    content = re.sub(r'@Min\(.*?\)', '', content)
    content = re.sub(r'@Max\(.*?\)', '', content)
    content = re.sub(r'@DecimalMin\(.*?\)', '', content)
    content = re.sub(r'@DecimalMax\(.*?\)', '', content)
    content = re.sub(r'@Builder\.Default', '', content)
    
    # Strip Lombok annotations
    content = re.sub(r'@Data', '', content)
    content = re.sub(r'@Getter', '', content)
    content = re.sub(r'@Setter', '', content)
    content = re.sub(r'@Builder', '', content)
    content = re.sub(r'@NoArgsConstructor', '', content)
    content = re.sub(r'@AllArgsConstructor', '', content)
    content = re.sub(r'import lombok\..*?;', '', content)

    # Ensure package is correct
    content = re.sub(r'package ch\.swissqcommerce\.backend\..*?;', f'package {package_name};', content, 1)

    # Clean double newlines and trailing spaces
    content = re.sub(r'\n\s*\n\s*\n', '\n\n', content)
    
    return content.strip()

# 2. Files to clean under domain/*/core/model
files_to_clean = [
    # Rewards
    ("reward/core/model/RewardPoints.java", "ch.swissqcommerce.backend.domain.reward.core.model", "RewardPoints"),
    ("reward/core/model/CustomerLoyalty.java", "ch.swissqcommerce.backend.domain.reward.core.model", "CustomerLoyalty"),
    # Feedback
    ("feedback/core/model/Feedback.java", "ch.swissqcommerce.backend.domain.feedback.core.model", "Feedback"),
    # Dispatch
    ("dispatch/core/model/VehicleConfig.java", "ch.swissqcommerce.backend.domain.dispatch.core.model", "VehicleConfig"),
    ("dispatch/core/model/GearScan.java", "ch.swissqcommerce.backend.domain.dispatch.core.model", "GearScan"),
    # Enrollment
    ("enrollment/core/model/OnboardingApplication.java", "ch.swissqcommerce.backend.domain.enrollment.core.model", "OnboardingApplication"),
    ("enrollment/core/model/Rider.java", "ch.swissqcommerce.backend.domain.enrollment.core.model", "Rider"),
    ("enrollment/core/model/RiderAcademyCertificate.java", "ch.swissqcommerce.backend.domain.enrollment.core.model", "RiderAcademyCertificate"),
    # Wholesaler
    ("wholesaler/core/model/Wholesaler.java", "ch.swissqcommerce.backend.domain.wholesaler.core.model", "Wholesaler"),
    ("wholesaler/core/model/PurchaseOrderItem.java", "ch.swissqcommerce.backend.domain.wholesaler.core.model", "PurchaseOrderItem"),
    ("wholesaler/core/model/B2BRestockOrder.java", "ch.swissqcommerce.backend.domain.wholesaler.core.model", "B2BRestockOrder"),
    ("wholesaler/core/model/WastageLog.java", "ch.swissqcommerce.backend.domain.wholesaler.core.model", "WastageLog"),
    ("wholesaler/core/model/PurchaseOrder.java", "ch.swissqcommerce.backend.domain.wholesaler.core.model", "PurchaseOrder"),
    # Payment
    ("payment/core/model/Payment.java", "ch.swissqcommerce.backend.domain.payment.core.model", "Payment"),
    # Transaction
    ("transaction/core/model/LedgerLine.java", "ch.swissqcommerce.backend.domain.transaction.core.model", "LedgerLine"),
    ("transaction/core/model/JournalEntry.java", "ch.swissqcommerce.backend.domain.transaction.core.model", "JournalEntry"),
    ("transaction/core/model/OrderItem.java", "ch.swissqcommerce.backend.domain.transaction.core.model", "OrderItem"),
    ("transaction/core/model/Order.java", "ch.swissqcommerce.backend.domain.transaction.core.model", "Order")
]

print("Executing Phase 1: Cleaning remaining polluted domains...")

for rel_path, package_name, class_name in files_to_clean:
    full_path = f"{base_path}/domain/{rel_path}"
    if os.path.exists(full_path):
        with open(full_path, "r") as f:
            content = f.read()
        
        # Check if it has already been stripped
        if "jakarta.persistence" in content or "@Entity" in content:
            # First, save a backup copy as Entity in adapter/out/persistence before stripping
            adapter_dir = os.path.dirname(full_path).replace("core/model", "adapter/out/persistence")
            os.makedirs(adapter_dir, exist_ok=True)
            
            # Map clean name to Entity name
            entity_name = f"{class_name}Entity"
            entity_path = f"{adapter_dir}/{entity_name}.java"
            
            entity_content = content.replace(f"class {class_name}", f"class {entity_name}")
            entity_content = entity_content.replace(f"package ch.swissqcommerce.backend.domain.{rel_path.split('/')[0]}.core.model;", 
                                                    f"package ch.swissqcommerce.backend.domain.{rel_path.split('/')[0]}.adapter.out.persistence;")
            
            # Save the JPA Entity
            with open(entity_path, "w") as f_out:
                f_out.write(entity_content)
            print(f"Created Entity: {entity_path}")
            
            # Now, strip the original core model
            stripped = strip_jpa_and_validation(content, package_name, class_name)
            
            # Manually inject getters/setters/constructors if they were stripped
            # Let's simple check if we need to generate getters/setters/constructors
            # To be 100% safe, we can append standard getters/setters dynamically, or just add Lombok @Getter/@Setter back
            # Wait, the earlier Lombok issues were resolved by compiling. Let's add @Getter/@Setter/etc back to the core model, 
            # but without any JPA annotations. That keeps it as a clean POJO with Lombok.
            # Yes! The compiler failed previously because Lombok was stripped or annotation processor failed, but with 
            # lombok configuration corrected, we can use lombok annotations. Let's make the stripped model a clean Lombok POJO.
            clean_lombok_model = f"""package {package_name};

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
""" + stripped[stripped.find("public class"): ]
            
            with open(full_path, "w") as f_out:
                f_out.write(clean_lombok_model)
            print(f"Cleaned Core Model: {full_path}")
    else:
        print(f"File not found: {full_path}")

print("Phase 1 complete!")
