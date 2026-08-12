import * as budgets from 'aws-cdk-lib/aws-budgets';
import { Construct } from 'constructs';

export interface CostAlarmProps {
  readonly namePrefix: string;
  readonly monthlyBudgetUsd: number;
  /** Where to send the alert. No budget is created without one. */
  readonly notifyEmail?: string;
}

/**
 * A monthly cost budget with two alerts: one when forecast spend crosses the
 * threshold, and one when actual spend does.
 *
 * <p>A forecast alert is the useful one — it fires while there is still time to
 * turn something off.
 */
export class CostAlarm extends Construct {
  constructor(scope: Construct, id: string, props: CostAlarmProps) {
    super(scope, id);

    if (!props.notifyEmail) {
      // eslint-disable-next-line no-console
      console.warn(
        '[budget] No budget email configured, so no cost alert will be created. ' +
          'Pass -c budgetEmail=you@example.com to enable it.',
      );
      return;
    }

    const subscribers = [{ subscriptionType: 'EMAIL', address: props.notifyEmail }];

    new budgets.CfnBudget(this, 'MonthlyBudget', {
      budget: {
        budgetName: `${props.namePrefix}-monthly`,
        budgetType: 'COST',
        timeUnit: 'MONTHLY',
        budgetLimit: { amount: props.monthlyBudgetUsd, unit: 'USD' },
      },
      notificationsWithSubscribers: [
        {
          notification: {
            notificationType: 'FORECASTED',
            comparisonOperator: 'GREATER_THAN',
            threshold: 80,
            thresholdType: 'PERCENTAGE',
          },
          subscribers,
        },
        {
          notification: {
            notificationType: 'ACTUAL',
            comparisonOperator: 'GREATER_THAN',
            threshold: 100,
            thresholdType: 'PERCENTAGE',
          },
          subscribers,
        },
      ],
    });
  }
}
