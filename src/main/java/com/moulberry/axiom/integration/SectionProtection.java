package com.moulberry.axiom.integration;

public interface SectionProtection {

    SectionProtection ALLOW = new SectionProtection() {
        @Override
        public SectionState getSectionState() {
            return SectionState.ALLOW;
        }

        @Override
        public boolean check(int wx, int wy, int wz) {
            return true;
        }
    };

    SectionProtection DENY = new SectionProtection() {
        @Override
        public SectionState getSectionState() {
            return SectionState.DENY;
        }

        @Override
        public boolean check(int wx, int wy, int wz) {
            return false;
        }
    };

    enum SectionState {
        ALLOW,
        DENY,
        CHECK
    }

    SectionState getSectionState();
    boolean check(int wx, int wy, int wz);

}
