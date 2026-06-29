// Components are re-exported from @swish/shared-ui — the single source of truth.
// design-system previously kept its own copies of AuthPortal/Skeleton, which
// drifted from shared-ui's. They now resolve to one implementation.
export {
	Skeleton,
	ProductCardSkeleton,
	ProductGridSkeleton,
	TableRowsSkeleton,
	GenericCardSkeleton,
	AuthPortal,
	Modal,
} from "@swish/shared-ui";
export type {
	SkeletonProps,
	AuthPortalProps,
	AuthSession,
	AuthRole,
	MfaMethod,
	ModalProps,
} from "@swish/shared-ui";
