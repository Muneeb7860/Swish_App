import os
import re

domain_root = 'backend/src/main/java/ch/swissqcommerce/backend/domain'

models_to_fix = [
    'Order.java', 'OrderItem.java', 'JournalEntry.java', 'LedgerLine.java',
    'Rider.java', 'RiderAcademyCertificate.java', 'OnboardingApplication.java',
    'B2BRestockOrder.java', 'Wholesaler.java', 'Payment.java',
    'GearScan.java', 'VehicleConfig.java', 'Feedback.java',
    'RewardPoints.java', 'CustomerLoyalty.java'
]

# Better regexes to strip annotations
def strip_annotations(content):
    # Strip jakarta imports
    content = re.sub(r'import\s+jakarta\.persistence\.[^;]+;\n?', '', content)
    content = re.sub(r'import\s+jakarta\.validation\.[^;]+;\n?', '', content)
    # Strip class-level JPA annotations
    content = re.sub(r'@Entity\s*\n', '', content)
    content = re.sub(r'@Table\([^\)]+\)\s*\n', '', content)
    content = re.sub(r'@NoArgsConstructor\s*\n', '', content)
    content = re.sub(r'@AllArgsConstructor\s*\n', '', content)
    
    # Strip field-level annotations
    content = re.sub(r'\s*@Id\s*\n', '\n', content)
    content = re.sub(r'\s*@Column\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@Column\s*\n', '\n', content)
    content = re.sub(r'\s*@NotNull\s*\n', '\n', content)
    content = re.sub(r'\s*@NotBlank\s*\n', '\n', content)
    content = re.sub(r'\s*@Size\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@DecimalMin\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@DecimalMax\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@Min\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@Max\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@Enumerated\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@JoinColumn\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@OneToOne\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@OneToMany\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@ManyToOne\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@ManyToMany\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@GeneratedValue\([^\)]+\)\s*\n', '\n', content)
    content = re.sub(r'\s*@GeneratedValue\s*\n', '\n', content)
    content = re.sub(r'\s*@ElementCollection\s*\n', '\n', content)
    content = re.sub(r'\s*@CollectionTable\([^\)]+\)\s*\n', '\n', content)
    
    return content

for root, dirs, files in os.walk(domain_root):
    if 'core/model' in root:
        for file in files:
            if file in models_to_fix:
                path = os.path.join(root, file)
                with open(path, 'r') as f:
                    content = f.read()

                clean_content = strip_annotations(content)

                if clean_content != content:
                    with open(path, 'w') as f:
                        f.write(clean_content)
                    print(f"Cleaned {path}")
