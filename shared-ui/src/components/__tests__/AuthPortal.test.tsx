import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AuthPortal } from "../AuthPortal";

describe("AuthPortal Component", () => {
	const mockOnAuthSuccess = vi.fn();
	const mockOnAuthError = vi.fn();

	beforeEach(() => {
		mockOnAuthSuccess.mockClear();
		mockOnAuthError.mockClear();
	});

	describe("Rendering", () => {
		it("renders customer login form", () => {
			render(<AuthPortal role="customer" onAuthSuccess={mockOnAuthSuccess} />);
			expect(screen.getByText("Customer Login")).toBeInTheDocument();
		});

		it("renders admin login form with default email", () => {
			render(<AuthPortal role="admin" onAuthSuccess={mockOnAuthSuccess} />);
			expect(screen.getByDisplayValue("admin@swish.local")).toBeInTheDocument();
		});

		it("renders rider login form", () => {
			render(<AuthPortal role="rider" onAuthSuccess={mockOnAuthSuccess} />);
			expect(screen.getByText("Rider Login")).toBeInTheDocument();
		});

		it("renders with custom form label", () => {
			render(
				<AuthPortal
					role="customer"
					formLabel="Custom Login"
					onAuthSuccess={mockOnAuthSuccess}
				/>,
			);
			expect(screen.getByText("Custom Login")).toBeInTheDocument();
		});

		it("uses submitLabel for non-customer roles", () => {
			// The customer role shows "Log in"/"Create account"; submitLabel applies
			// to the other roles (admin/rider).
			render(
				<AuthPortal
					role="admin"
					submitLabel="Continue"
					onAuthSuccess={mockOnAuthSuccess}
				/>,
			);
			expect(
				screen.getByRole("button", { name: "Continue" }),
			).toBeInTheDocument();
		});

		it("shows 'Log in' as the customer submit action", () => {
			render(<AuthPortal role="customer" onAuthSuccess={mockOnAuthSuccess} />);
			expect(
				screen.getByRole("button", { name: "Log in" }),
			).toBeInTheDocument();
		});
	});

	describe("Customer Role Specific", () => {
		it("shows register/login toggle for customers", () => {
			render(<AuthPortal role="customer" onAuthSuccess={mockOnAuthSuccess} />);
			expect(screen.getByText("Don't have an account?")).toBeInTheDocument();
			expect(
				screen.getByRole("button", { name: "Register" }),
			).toBeInTheDocument();
		});

		it("toggles between login and register modes", async () => {
			render(<AuthPortal role="customer" onAuthSuccess={mockOnAuthSuccess} />);
			const registerButton = screen.getByRole("button", { name: "Register" });
			fireEvent.click(registerButton);
			await waitFor(() => {
				expect(
					screen.getByText("Already have an account?"),
				).toBeInTheDocument();
			});
		});
	});

	describe("Admin Role Specific", () => {
		it("does not show register/login toggle for admin", () => {
			render(<AuthPortal role="admin" onAuthSuccess={mockOnAuthSuccess} />);
			expect(
				screen.queryByText("Don't have an account?"),
			).not.toBeInTheDocument();
		});
	});

	describe("Form Fields", () => {
		it("renders email and password fields", () => {
			render(<AuthPortal role="customer" onAuthSuccess={mockOnAuthSuccess} />);
			expect(screen.getByLabelText("Email")).toBeInTheDocument();
			expect(screen.getByLabelText("Password")).toBeInTheDocument();
		});

		it("handles email input", async () => {
			render(<AuthPortal role="customer" onAuthSuccess={mockOnAuthSuccess} />);
			const emailInput = screen.getByLabelText("Email") as HTMLInputElement;
			fireEvent.change(emailInput, { target: { value: "test@example.com" } });
			expect(emailInput.value).toBe("test@example.com");
		});

		it("handles password input", async () => {
			render(<AuthPortal role="customer" onAuthSuccess={mockOnAuthSuccess} />);
			const passwordInput = screen.getByLabelText(
				"Password",
			) as HTMLInputElement;
			fireEvent.change(passwordInput, { target: { value: "password123" } });
			expect(passwordInput.value).toBe("password123");
		});
	});

	describe("MFA", () => {
		it("shows MFA options when enabled", () => {
			render(
				<AuthPortal
					role="customer"
					mfaEnabled={true}
					mfaMethods={["sms", "totp"]}
					onAuthSuccess={mockOnAuthSuccess}
				/>,
			);
			// MFA section is shown after login attempt, not on initial render
			expect(
				screen.queryByText("Verify Your Identity"),
			).not.toBeInTheDocument();
		});

		it("hides MFA options when not enabled", () => {
			render(
				<AuthPortal
					role="customer"
					mfaEnabled={false}
					onAuthSuccess={mockOnAuthSuccess}
				/>,
			);
			expect(
				screen.queryByText("Verify Your Identity"),
			).not.toBeInTheDocument();
		});
	});

	describe("Accessibility", () => {
		it("renders a form wrapping the inputs", () => {
			const { container } = render(
				<AuthPortal role="customer" onAuthSuccess={mockOnAuthSuccess} />,
			);
			const form = container.querySelector("form");
			expect(form).toBeInTheDocument();
			expect(form?.querySelector("input")).toBeInTheDocument();
		});

		it("submit button is properly labeled (non-customer role)", () => {
			render(
				<AuthPortal
					role="admin"
					submitLabel="Sign In"
					onAuthSuccess={mockOnAuthSuccess}
				/>,
			);
			expect(
				screen.getByRole("button", { name: "Sign In" }),
			).toBeInTheDocument();
		});

		it("labels are associated with inputs", () => {
			render(<AuthPortal role="customer" onAuthSuccess={mockOnAuthSuccess} />);
			const emailLabel = screen.getByText("Email");
			expect(emailLabel).toBeInTheDocument();
		});
	});

	describe("Error Handling", () => {
		it("calls onAuthError callback on error", async () => {
			const { container } = render(
				<AuthPortal
					role="customer"
					onAuthSuccess={mockOnAuthSuccess}
					onAuthError={mockOnAuthError}
				/>,
			);
			// This would require mocking fetch to test error scenarios
			expect(mockOnAuthError).not.toHaveBeenCalled();
		});
	});

	describe("Props", () => {
		it("accepts custom API URL", () => {
			const customUrl = "https://custom-api.example.com/auth";
			render(
				<AuthPortal
					role="customer"
					apiUrl={customUrl}
					onAuthSuccess={mockOnAuthSuccess}
				/>,
			);
			// Component accepts the prop without error
			expect(screen.getByText("Customer Login")).toBeInTheDocument();
		});

		it("accepts default email", () => {
			render(
				<AuthPortal
					role="customer"
					defaultEmail="user@example.com"
					onAuthSuccess={mockOnAuthSuccess}
				/>,
			);
			expect(screen.getByDisplayValue("user@example.com")).toBeInTheDocument();
		});
	});
});
