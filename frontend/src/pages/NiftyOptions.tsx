import { useEffect, useState } from 'react';
import { api, type OptionOpportunity } from '../services/api';
import { OptionChain } from '../components/dashboard/OptionChain';
import { OptionOpportunityCard } from '../components/dashboard/OptionOpportunityCard';

export function NiftyOptions() {
  const [expiries, setExpiries] = useState<string[]>([]);
  const [selectedExpiry, setSelectedExpiry] = useState('');

  const [opportunity, setOpportunity] =
    useState<OptionOpportunity | null>(null);

  const [loadingOpportunity, setLoadingOpportunity] =
    useState(false);

  const [opportunityError, setOpportunityError] =
    useState('');

  // Load active expiries
  useEffect(() => {
    const loadExpiries = async () => {
      try {
        const response = await api.niftyExpiries(5);

        setExpiries(response.expiries);

        if (response.expiries.length > 0) {
          setSelectedExpiry(response.expiries[0]);
        }
      } catch (error) {
        console.error('Failed to load NIFTY expiries', error);
      }
    };

    void loadExpiries();
  }, []);

  // Load opportunity whenever expiry changes
  useEffect(() => {
    if (!selectedExpiry) return;

    let active = true;

    const loadOpportunity = async () => {
      try {
        setLoadingOpportunity(true);
        setOpportunityError('');

        const response =
          await api.niftyOptionOpportunity(
            selectedExpiry
          );

        if (active) {
          setOpportunity(response.data);
        }
      } catch (error) {
        if (active) {
          setOpportunityError(
            error instanceof Error
              ? error.message
              : 'Unable to load option opportunity'
          );
        }
      } finally {
        if (active) {
          setLoadingOpportunity(false);
        }
      }
    };

    void loadOpportunity();

    return () => {
      active = false;
    };
  }, [selectedExpiry]);

  return (
    <main>

      <div className="heading">
        <div>
          <small>NIFTY OPTIONS</small>

          <h1>Options Terminal</h1>

          <p>
            Live NIFTY option chain and ML-powered
            opportunity analysis.
          </p>
        </div>
      </div>

      {!selectedExpiry && (
        <section className="panel">
          <p className="api-state">
            Loading available expiries…
          </p>
        </section>
      )}

      {selectedExpiry && (
        <>
          <OptionChain
            expiry={selectedExpiry}
            expiries={expiries}
            onExpiryChange={setSelectedExpiry}
          />

          <OptionOpportunityCard
            opportunity={opportunity}
            loading={loadingOpportunity}
            error={opportunityError}
          />
        </>
      )}

    </main>
  );
}
